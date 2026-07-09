package com.databundleHum.OnetBundleHub.services;

import com.databundleHum.OnetBundleHub.dtos.*;
import com.databundleHum.OnetBundleHub.dtos.AdminResellerResponse;
import com.databundleHum.OnetBundleHub.dtos.response.*;
import com.databundleHum.OnetBundleHub.entity.*;
import com.databundleHum.OnetBundleHub.entity.WalletTransaction.TransactionType;
import com.databundleHum.OnetBundleHub.repos.*;
import com.databundleHum.OnetBundleHub.security.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Super Admin service layer.
 *
 * Covers:
 *  - Platform dashboard KPIs
 *  - User management (list, activate/deactivate)
 *  - Reseller application review (approve / reject + auto-refund on rejection)
 *  - Payout processing (mark PAID / reject)
 *  - Platform pricing management (public + reseller prices per network/GB)
 *  - All-orders and all-transactions views
 *  - Slug backfill for approved resellers missing a store slug
 *
 * Changes from previous version:
 *  - approveReseller() now generates a unique store slug via
 *    ResellerServiceImpl.generateUniqueSlug() and persists it on the profile.
 *  - approveReseller() accepts an optional slug override in the request body.
 *  - markPayoutPaid() now decrements ResellerProfile.profitPaidGhc instead of
 *    debiting the personal wallet — reseller profit and wallet are separate balances
 *    per architecture §3.7 and §9.
 *  - backfillMissingSlugs() added to recover approved profiles with null store_slug
 *    (e.g. approved before slug generation logic existed).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository              userRepository;
    private final OrderRepository             orderRepository;
    private final ResellerProfileRepository   resellerProfileRepository;
    private final PayoutRepository            payoutRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PlatformSettingsRepository  platformSettingsRepository;
    private final WalletService               walletService;
    private final NotificationService         notificationService;
    private final ResellerServiceImpl         resellerService; // for generateUniqueSlug()

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        long totalUsers     = userRepository.count();
        long totalResellers = userRepository.countByRole(User.Role.RESELLER);
        long pendingApps    = resellerProfileRepository
                .countByStatus(ResellerProfile.ResellerStatus.PENDING);
        long totalOrders    = orderRepository.count();
        long pendingPayouts = payoutRepository.countByStatus(Payout.PayoutStatus.PENDING);

        BigDecimal totalRevenue = orderRepository.sumSellingPriceByStatus(Order.OrderStatus.DELIVERED);
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        BigDecimal walletLiabilities = userRepository.sumWalletBalances();
        if (walletLiabilities == null) walletLiabilities = BigDecimal.ZERO;

        BigDecimal pendingPayoutsTotal = payoutRepository.sumAmountByStatus(Payout.PayoutStatus.PENDING);
        if (pendingPayoutsTotal == null) pendingPayoutsTotal = BigDecimal.ZERO;

        log.debug("Admin dashboard fetched: users={} orders={}", totalUsers, totalOrders);

        return AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .totalResellers(totalResellers)
                .pendingResellerApplications(pendingApps)
                .totalOrders(totalOrders)
                .pendingPayouts(pendingPayouts)
                .totalRevenueGhc(totalRevenue)
                .totalWalletLiabilitiesGhc(walletLiabilities)
                .totalPendingPayoutsGhc(pendingPayoutsTotal)
                .build();
    }

    // ── User management ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toUserResponse);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(UUID userId) {
        return toUserResponse(findUserOrThrow(userId));
    }

    @Transactional
    public AdminUserResponse setUserActive(UUID adminId, UUID targetUserId, boolean active) {
        User target = findUserOrThrow(targetUserId);
        target.setActive(active);
        userRepository.save(target);
        log.info("Admin userId={} set userId={} active={}", adminId, targetUserId, active);
        return toUserResponse(target);
    }

    // ── Reseller management ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AdminResellerResponse> getResellers(Pageable pageable) {
        return resellerProfileRepository.findAll(pageable).map(this::toResellerResponse);
    }

    @Transactional(readOnly = true)
    public Page<AdminResellerResponse> getResellersByStatus(
            ResellerProfile.ResellerStatus status, Pageable pageable) {
        return resellerProfileRepository.findByStatus(status, pageable)
                .map(this::toResellerResponse);
    }

    /**
     * Approve a reseller application.
     *
     * Steps:
     *  1. Validate the profile is still PENDING.
     *  2. Generate a unique slug from the reseller's full name (or use the
     *     admin-supplied slug override from the request).
     *  3. Set profile.status → APPROVED, profile.storeSlug, profile.approvedAt.
     *  4. Set user.role → RESELLER.
     *  5. Send approval email with the store link.
     *
     * The slug is immutable once set — admins cannot change it after approval
     * because live store links and QR codes would break.
     *
     * @param request  may include an optional {@code slugOverride}; if null or
     *                 blank, the slug is auto-generated from the user's name.
     */
    @Transactional
    public AdminResellerResponse approveReseller(UUID adminId, UUID profileId,
                                                 AdminResellerActionRequest request) {
        ResellerProfile profile = findProfileOrThrow(profileId);

        if (profile.getStatus() != ResellerProfile.ResellerStatus.PENDING) {
            throw new ValidationException(
                    "Cannot approve application with status: " + profile.getStatus());
        }

        User admin    = findUserOrThrow(adminId);
        User reseller = profile.getUser();

        // ── Slug generation ───────────────────────────────────────────────────
        String slug;
        if (request.getSlugOverride() != null && !request.getSlugOverride().isBlank()) {
            // Admin-supplied override — still validate uniqueness
            slug = request.getSlugOverride().trim().toLowerCase();
            if (resellerProfileRepository.existsByStoreSlug(slug)) {
                throw new ValidationException(
                        "The slug '" + slug + "' is already taken. Choose another.");
            }
        } else {
            slug = resellerService.generateUniqueSlug(reseller.getFullName());
        }

        profile.setStatus(ResellerProfile.ResellerStatus.APPROVED);
        profile.setStoreSlug(slug);
        profile.setApprovedAt(LocalDateTime.now());
        profile.setApprovedBy(admin);
        if (request.getNote() != null) profile.setApplicationNote(request.getNote());
        resellerProfileRepository.save(profile);

        reseller.setRole(User.Role.RESELLER);
        userRepository.save(reseller);

        log.info("Reseller APPROVED: profileId={} userId={} slug={} by adminId={}",
                profileId, reseller.getId(), slug, adminId);

        return toResellerResponse(profile);
    }

    /**
     * Reject a reseller application.
     * Refunds the GHS 20 registration fee back to the applicant's wallet.
     */
    @Transactional
    public AdminResellerResponse rejectReseller(UUID adminId, UUID profileId,
                                                AdminResellerActionRequest request) {
        ResellerProfile profile = findProfileOrThrow(profileId);

        if (profile.getStatus() != ResellerProfile.ResellerStatus.PENDING) {
            throw new ValidationException(
                    "Cannot reject application with status: " + profile.getStatus());
        }

        User reseller = profile.getUser();

        profile.setStatus(ResellerProfile.ResellerStatus.REJECTED);
        profile.setRejectionReason(request.getNote());
        resellerProfileRepository.save(profile);

        // Refund GHS 20 registration fee
        walletService.credit(reseller.getId(), new BigDecimal("20.00"),
                TransactionType.REFUND,
                "Reseller registration fee refund — application rejected",
                "RESELLER_REJECT_" + profileId);

        notificationService.sendResellerRejectedEmail(
                reseller.getEmail(), reseller.getFullName(), request.getNote());

        log.info("Reseller REJECTED: profileId={} userId={} by adminId={} reason={}",
                profileId, reseller.getId(), adminId, request.getNote());

        return toResellerResponse(profile);
    }

    // ── Slug backfill ─────────────────────────────────────────────────────────

    /**
     * Backfill store slugs for any APPROVED reseller profiles that are missing one.
     *
     * This covers resellers who were approved before slug generation was introduced.
     * Safe to call multiple times — only profiles with a null store_slug are touched.
     *
     * Expose via an admin-only endpoint, e.g.:
     *   POST /admin/resellers/backfill-slugs
     *
     * @return number of profiles that were updated
     */
    @Transactional
    public int backfillMissingSlugs() {
        List<ResellerProfile> profiles = resellerProfileRepository
                .findByStatusAndStoreSlugIsNull(ResellerProfile.ResellerStatus.APPROVED);

        if (profiles.isEmpty()) {
            log.info("[SLUG BACKFILL] No profiles found with missing slugs.");
            return 0;
        }

        int count = 0;
        for (ResellerProfile profile : profiles) {
            String slug = resellerService.generateUniqueSlug(profile.getUser().getFullName());
            profile.setStoreSlug(slug);
            resellerProfileRepository.save(profile);
            log.info("[SLUG BACKFILL] Set slug='{}' for profileId={} userId={}",
                    slug, profile.getId(), profile.getUser().getId());
            count++;
        }

        log.info("[SLUG BACKFILL] Complete. {} profile(s) updated.", count);
        return count;
    }

    // ── Payout management ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PayoutResponse> getPayouts(Payout.PayoutStatus status, Pageable pageable) {
        if (status != null) {
            return payoutRepository.findByStatusOrderByCreatedAtAsc(status, pageable)
                    .map(this::toPayoutResponse);
        }
        return payoutRepository.findAll(pageable).map(this::toPayoutResponse);
    }

    /**
     * Mark a payout as PAID.
     *
     * Architecture §3.7 / §9: reseller profit balance and personal wallet are separate.
     * When a payout is marked paid, we increment profitPaidGhc on the ResellerProfile
     * (reducing their available profit balance). The personal wallet is NOT touched.
     *
     * The reseller's personal wallet is only for their own data purchases (top-ups).
     * Storefront profit is withdrawn via payout, not via wallet debit.
     */
    @Transactional
    public PayoutResponse markPayoutPaid(UUID adminId, Long payoutId,
                                         AdminPayoutPaidRequest request) {
        Payout payout = findPayoutOrThrow(payoutId);

        if (payout.getStatus() != Payout.PayoutStatus.PENDING &&
                payout.getStatus() != Payout.PayoutStatus.PROCESSING) {
            throw new ValidationException(
                    "Payout cannot be marked PAID. Current status: " + payout.getStatus());
        }

        User admin = findUserOrThrow(adminId);

        // Increment the paid-out amount on the reseller's profit balance
        ResellerProfile profile = resellerProfileRepository
                .findByUser(payout.getReseller())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No reseller profile found for payout reseller."));

        BigDecimal available = profile.getAvailableProfitGhc();
        if (available.compareTo(payout.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    "Reseller's available profit (GHS " + available
                            + ") is less than payout amount (GHS " + payout.getAmount() + ").");
        }

        profile.setProfitPaidGhc(profile.getProfitPaidGhc().add(payout.getAmount()));
        resellerProfileRepository.save(profile);

        payout.setStatus(Payout.PayoutStatus.PAID);
        payout.setPaidAt(LocalDateTime.now());
        payout.setPaidBy(admin);
        payout.setAdminNote(request.getAdminNote());
        payoutRepository.save(payout);

        notificationService.sendPayoutPaidAlert(
                payout.getReseller().getEmail(),
                payout.getReseller().getFullName(),
                payout.getAmount());

        log.info("Payout PAID: payoutId={} resellerId={} amount={} by adminId={}",
                payoutId, payout.getReseller().getId(), payout.getAmount(), adminId);

        return toPayoutResponse(payout);
    }

    /**
     * Reject a payout request. Does NOT touch profit or wallet balance.
     */
    @Transactional
    public PayoutResponse rejectPayout(UUID adminId, Long payoutId,
                                       AdminPayoutRejectRequest request) {
        Payout payout = findPayoutOrThrow(payoutId);

        if (payout.getStatus() != Payout.PayoutStatus.PENDING) {
            throw new ValidationException(
                    "Payout cannot be rejected. Current status: " + payout.getStatus());
        }

        payout.setStatus(Payout.PayoutStatus.REJECTED);
        payout.setAdminNote(request.getReason());
        payoutRepository.save(payout);

        notificationService.sendPayoutRejectedAlert(
                payout.getReseller().getEmail(),
                payout.getReseller().getFullName(),
                payout.getAmount(),
                request.getReason());

        log.info("Payout REJECTED: payoutId={} resellerId={} by adminId={} reason={}",
                payoutId, payout.getReseller().getId(), adminId, request.getReason());

        return toPayoutResponse(payout);
    }

    // ── Platform pricing ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AdminPricingResponse> getPricingTable(Pageable pageable) {
        return platformSettingsRepository.findAll(pageable).map(this::toPricingResponse);
    }

    /**
     * Upsert a pricing row.
     * Enforces: resellerPrice < publicPrice (to guarantee reseller margin).
     */
    @Transactional
    public AdminPricingResponse upsertPricing(UUID adminId, AdminPricingRequest request) {
        if (request.getResellerPriceGhc().compareTo(request.getPublicPriceGhc()) >= 0) {
            throw new ValidationException(
                    "Reseller price (GHS " + request.getResellerPriceGhc()
                            + ") must be strictly less than public price (GHS "
                            + request.getPublicPriceGhc() + ").");
        }

        PlatformSettings settings = platformSettingsRepository
                .findByNetworkAndCapacityGb(request.getNetwork(), request.getCapacityGb())
                .orElseGet(() -> PlatformSettings.builder()
                        .network(request.getNetwork())
                        .capacityGb(request.getCapacityGb())
                        .build());

        settings.setPublicPriceGhc(request.getPublicPriceGhc());
        settings.setResellerPriceGhc(request.getResellerPriceGhc());
        settings.setActive(request.isActive());
        settings = platformSettingsRepository.save(settings);

        log.info("Pricing upserted by adminId={}: network={} gb={} public={} reseller={}",
                adminId, request.getNetwork(), request.getCapacityGb(),
                request.getPublicPriceGhc(), request.getResellerPriceGhc());

        return toPricingResponse(settings);
    }

    @Transactional
    public AdminPricingResponse toggleBundleActive(UUID adminId, Long settingsId, boolean active) {
        PlatformSettings settings = platformSettingsRepository.findById(settingsId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pricing row not found: " + settingsId));
        settings.setActive(active);
        platformSettingsRepository.save(settings);
        log.info("Bundle active={}: settingsId={} by adminId={}", active, settingsId, adminId);
        return toPricingResponse(settings);
    }

    // ── All-orders / all-transactions ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(this::toOrderResponse);
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getAllTransactions(Pageable pageable) {
        return walletTransactionRepository.findAll(pageable).map(this::toTxResponse);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private ResellerProfile findProfileOrThrow(UUID profileId) {
        return resellerProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reseller profile not found: " + profileId));
    }

    private Payout findPayoutOrThrow(Long payoutId) {
        return payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payout not found: " + payoutId));
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private AdminUserResponse toUserResponse(User u) {
        return AdminUserResponse.builder()
                .id(u.getId())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .phone(u.getPhone())
                .role(u.getRole().name())
                .walletBalance(u.getWalletBalance())
                .active(u.isActive())
                .createdAt(u.getCreatedAt())
                .build();
    }

    private AdminResellerResponse toResellerResponse(ResellerProfile p) {
        return AdminResellerResponse.builder()
                .profileId(p.getId())
                .userId(p.getUser().getId())
                .fullName(p.getUser().getFullName())
                .email(p.getUser().getEmail())
                .phone(p.getUser().getPhone())
                .status(p.getStatus().name())
                .storeSlug(p.getStoreSlug())
                .storeName(p.getEffectiveStoreName())
                .applicationNote(p.getApplicationNote())
                .rejectionReason(p.getRejectionReason())
                .totalRevenueGhc(p.getTotalRevenueGhc())
                .totalCostGhc(p.getTotalCostGhc())
                .totalProfitGhc(p.getTotalProfitGhc())
                .availableProfitGhc(p.getAvailableProfitGhc())
                .approvedAt(p.getApprovedAt())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private PayoutResponse toPayoutResponse(Payout p) {
        return PayoutResponse.builder()
                .id(p.getId())
                .amount(p.getAmount())
                .mobileMoneyNumber(p.getMobileMoneyNumber())
                .network(p.getNetwork().name())
                .status(p.getStatus().name())
                .adminNote(p.getAdminNote())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private AdminPricingResponse toPricingResponse(PlatformSettings s) {
        return AdminPricingResponse.builder()
                .id(s.getId())
                .network(s.getNetwork().name())
                .capacityGb(s.getCapacityGb())
                .publicPriceGhc(s.getPublicPriceGhc())
                .resellerPriceGhc(s.getResellerPriceGhc())
                .active(s.isActive())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private OrderResponse toOrderResponse(Order o) {
        return OrderResponse.builder()
                .id(o.getId())
                .phoneNumber(o.getPhoneNumber())
                .network(o.getNetwork().name())
                .capacityGb(o.getCapacityGb())
                .costPriceGhc(o.getCostPriceGhc())
                .sellingPriceGhc(o.getSellingPriceGhc())
                .paymentMethod(o.getPaymentMethod().name())
                .paystackRef(o.getPaystackRef())
                .status(o.getStatus().name())
                .guest(o.isGuest())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .build();
    }

    private WalletTransactionResponse toTxResponse(WalletTransaction t) {
        return WalletTransactionResponse.builder()
                .id(t.getId())
                .type(t.getType().name())
                .amount(t.getAmount())
                .balanceAfter(t.getBalanceAfter())
                .reference(t.getReference())
                .description(t.getDescription())
                .createdAt(t.getCreatedAt())
                .build();
    }
}