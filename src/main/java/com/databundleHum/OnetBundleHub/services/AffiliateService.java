package com.databundleHum.OnetBundleHub.services;

import com.databundleHum.OnetBundleHub.dtos.AffiliateActivateResponse;
import com.databundleHum.OnetBundleHub.dtos.AffiliateCommissionResponse;
import com.databundleHum.OnetBundleHub.dtos.AffiliateDashboardResponse;
import com.databundleHum.OnetBundleHub.dtos.PayoutRequest;
import com.databundleHum.OnetBundleHub.dtos.response.PayoutResponse;
import com.databundleHum.OnetBundleHub.entity.CommissionTransaction;
import com.databundleHum.OnetBundleHub.entity.Order;
import com.databundleHum.OnetBundleHub.entity.Payout;
import com.databundleHum.OnetBundleHub.entity.ResellerProfile;
import com.databundleHum.OnetBundleHub.entity.User;
import com.databundleHum.OnetBundleHub.repos.CommissionTransactionRepository;
import com.databundleHum.OnetBundleHub.repos.PayoutRepository;
import com.databundleHum.OnetBundleHub.repos.UserRepository;
import com.databundleHum.OnetBundleHub.security.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Affiliate programme — opt-in/out, code generation, dashboard stats, payouts.
 *
 * Any USER (including resellers) can activate the affiliate programme.
 * No fee, no approval, no expiry. Deactivation is instant and reversible.
 *
 * Affiliate code format (architecture §4.1):
 *   8 characters, uppercase alphanumeric (A-Z, 0-9), e.g. "A3KP9WZQ".
 *   Generated once on first activation using SecureRandom.
 *   Never changes — existing referral links remain valid (though inactive
 *   after deactivation).
 *
 * Referral URL built by this service:
 *   https://{baseUrl}/a/{affiliateCode}
 *
 * Self-referral prevention:
 *   Enforced at registration in AuthService.register() by checking that
 *   the affiliate code does not resolve to the signing-up user's own ID.
 *   This service does not need to re-check it.
 *
 * Payouts:
 *   Affiliate commission lives in User.affiliateEarningsGhc — a SEPARATE
 *   balance from User.walletBalance (topped-up/spendable money).
 *   requestPayout() checks and draws ONLY against affiliateEarningsGhc;
 *   it never reads or touches walletBalance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AffiliateService {

    private static final String CODE_CHARS   = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int    CODE_LENGTH  = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository                  userRepository;
    private final CommissionTransactionRepository commissionTransactionRepository;
    private final PayoutRepository                payoutRepository;
    private final AppUrlProvider                  appUrlProvider;

    @Value("${app.affiliate.min-payout-ghc:5.00}")
    private BigDecimal minPayoutGhc;

    // ── Activate ──────────────────────────────────────────────────────────────

    /**
     * Activate the affiliate programme for a user.
     *
     * If the user already has an affiliateCode from a previous activation,
     * it is reused (to preserve existing referral links).
     * If they never activated before, a new 8-char code is generated.
     *
     * @return the activation response including the referral URL
     */
    @Transactional
    public AffiliateActivateResponse activate(UUID userId) {
        User user = findUserOrThrow(userId);

        if (user.isAffiliate()) {
            // Already active — return current state without modifying anything
            log.debug("[AFFILIATE] Already active: userId={}", userId);
            return buildActivateResponse(user);
        }

        // Generate code only if this is the very first activation
        if (user.getAffiliateCode() == null) {
            String code = generateUniqueCode();
            user.setAffiliateCode(code);
            log.info("[AFFILIATE] Code generated: userId={} code={}", userId, code);
        }

        user.setAffiliate(true);
        userRepository.save(user);

        log.info("[AFFILIATE] Activated: userId={} code={}", userId, user.getAffiliateCode());
        return buildActivateResponse(user);
    }

    // ── Deactivate ────────────────────────────────────────────────────────────

    /**
     * Deactivate the affiliate programme for a user.
     *
     * The affiliateCode is retained in the DB.
     * The /a/{code} redirect handler will return a graceful "inactive" response.
     * Already-earned commissions are NOT affected — affiliateEarningsGhc is
     * untouched by deactivation and remains payable out.
     */
    @Transactional
    public void deactivate(UUID userId) {
        User user = findUserOrThrow(userId);

        if (!user.isAffiliate()) {
            log.debug("[AFFILIATE] Already inactive: userId={}", userId);
            return;
        }

        user.setAffiliate(false);
        userRepository.save(user);

        log.info("[AFFILIATE] Deactivated: userId={} code={}", userId, user.getAffiliateCode());
    }

    // ── Dashboard stats ───────────────────────────────────────────────────────

    /**
     * Fetch the affiliate dashboard summary.
     *
     * Returns:
     *   - Referral link
     *   - Total referred users who have made at least one purchase
     *   - Total commission earned (all time, non-reversed)
     *   - This month's commission
     *   - Current payout-eligible earnings balance (affiliateEarningsGhc) —
     *     NOT walletBalance, since affiliate earnings are a separate pot.
     *
     * Only available when user.isAffiliate == true.
     */
    @Transactional(readOnly = true)
    public AffiliateDashboardResponse getDashboard(UUID userId) {
        User user = findActiveAffiliateOrThrow(userId);

        BigDecimal totalEarned = commissionTransactionRepository
                .sumEarnedByAffiliate(userId);

        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        BigDecimal monthEarned = commissionTransactionRepository
                .sumEarnedByAffiliateThisMonth(userId, monthStart);

        long referredUserCount = commissionTransactionRepository
                .countDistinctReferredUsersByAffiliate(userId);

        // Total sign-ups via referral link (may be > referredUserCount since not
        // all signed-up users will have placed orders yet)
        long totalSignUps = userRepository.countByReferredByAffiliateId(userId);

        return AffiliateDashboardResponse.builder()
                .userId(userId)
                .affiliateCode(user.getAffiliateCode())
                .referralUrl(buildReferralUrl(user.getAffiliateCode()))
                .totalSignUps(totalSignUps)
                .referredUsersWithOrders(referredUserCount)
                .totalCommissionEarnedGhc(totalEarned)
                .thisMonthCommissionGhc(monthEarned)
                // NOTE: this used to be walletBalanceGhc. Affiliate earnings
                // are a separate, payout-only balance — surface THAT here,
                // not the spendable wallet balance.
                .availableEarningsGhc(user.getAffiliateEarningsGhc())
                .build();
    }

    // ── Commission history ────────────────────────────────────────────────────

    /**
     * Paginated commission history for the affiliate dashboard table.
     *
     * Each row shows: date, order reference, referred user (masked),
     * bundle info, commission amount, and whether it was reversed.
     */
    @Transactional(readOnly = true)
    public Page<AffiliateCommissionResponse> getCommissionHistory(UUID userId, Pageable pageable) {
        User user = findActiveAffiliateOrThrow(userId);

        return commissionTransactionRepository
                .findByAffiliateUserOrderByCreatedAtDesc(user, pageable)
                .map(this::toCommissionResponse);
    }

    // ── Payouts ───────────────────────────────────────────────────────────────

    /**
     * Request a cash-out of affiliate commission earnings.
     *
     * Checked and drawn ONLY against User.affiliateEarningsGhc — this method
     * never reads or debits walletBalance. Topped-up wallet money can never
     * be paid out this way.
     *
     * The requested amount is reserved immediately (deducted from
     * affiliateEarningsGhc on request, not on admin approval), so an
     * affiliate cannot request more than their available balance twice
     * before the first request is processed.
     */
    @Transactional
    public PayoutResponse requestPayout(UUID userId, PayoutRequest request) {
        User user = findActiveAffiliateOrThrow(userId);

        if (request.getAmount().compareTo(minPayoutGhc) < 0) {
            throw new MinPayoutNotMetException(
                    "Minimum payout is GHS " + minPayoutGhc
                            + ". Requested: GHS " + request.getAmount());
        }

        BigDecimal available = user.getAffiliateEarningsGhc();
        if (available.compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    "Available affiliate earnings GHS " + available
                            + " is insufficient for payout of GHS " + request.getAmount());
        }

        // Reserve the requested amount immediately — never touches walletBalance.
        user.setAffiliateEarningsGhc(available.subtract(request.getAmount()));
        userRepository.save(user);

        Payout payout = Payout.builder()
                .reseller(user) // generic "recipient" field — see Payout.source for context
                .amount(request.getAmount())
                .mobileMoneyNumber(request.getMobileMoneyNumber())
                .network(request.getNetwork())
                .status(Payout.PayoutStatus.PENDING)
                .source(Payout.PayoutSource.AFFILIATE_COMMISSION)
                .build();

        payout = payoutRepository.save(payout);
        log.info("[AFFILIATE] Payout requested: userId={} amount={} payoutId={} " +
                        "earningsBalanceAfter={}",
                userId, request.getAmount(), payout.getId(), user.getAffiliateEarningsGhc());

        return toPayoutResponse(payout);
    }

    @Transactional(readOnly = true)
    public Page<PayoutResponse> getPayoutHistory(UUID userId, Pageable pageable) {
        User user = findActiveAffiliateOrThrow(userId);
        return payoutRepository
                .findByResellerAndSourceOrderByCreatedAtDesc(
                        user, Payout.PayoutSource.AFFILIATE_COMMISSION, pageable)
                .map(this::toPayoutResponse);
    }

    // ── Cookie-based referral resolution (called from AuthService) ────────────

    /**
     * Resolve an affiliate code from the registration cookie to a User entity.
     *
     * Returns null if:
     *   - Code is null or blank (no cookie was set).
     *   - Code does not match any user.
     *   - The matching user is not currently an active affiliate.
     *
     * Self-referral check is done in AuthService.register() after calling this,
     * by comparing the returned user's ID against the newly registered user's ID.
     *
     * @param affiliateCode  value of the ref_affiliate cookie at registration time
     * @return               the affiliate User, or null if unresolvable
     */
    @Transactional(readOnly = true)
    public User resolveAffiliateCode(String affiliateCode) {
        if (affiliateCode == null || affiliateCode.isBlank()) return null;

        return userRepository.findByAffiliateCode(affiliateCode)
                .filter(User::isAffiliate)
                .orElse(null);
    }

    /**
     * Resolve a reseller referral slug to a User entity.
     *
     * Used by AuthService.register() to set User.referredByReseller.
     * The slug belongs to a ResellerProfile; we need the reseller's User.
     *
     * Returns null if the slug doesn't match any approved reseller profile.
     *
     * @param resellerSlug  value of the ref_reseller_id cookie at registration time
     * @return              the reseller User, or null if unresolvable
     */
    @Transactional(readOnly = true)
    public User resolveResellerSlug(String resellerSlug) {
        if (resellerSlug == null || resellerSlug.isBlank()) return null;

        return userRepository.findByApprovedResellerSlug(
                resellerSlug,
                ResellerProfile.ResellerStatus.APPROVED
        ).orElse(null);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private User findActiveAffiliateOrThrow(UUID userId) {
        User user = findUserOrThrow(userId);
        if (!user.isAffiliate()) {
            throw new AffiliateNotActiveException(
                    "Affiliate programme is not active for this account.");
        }
        return user;
    }

    private String buildReferralUrl(String code) {
        return appUrlProvider.getBaseUrl() + "/a/" + code;
    }

    private AffiliateActivateResponse buildActivateResponse(User user) {
        return AffiliateActivateResponse.builder()
                .affiliateCode(user.getAffiliateCode())
                .referralUrl(buildReferralUrl(user.getAffiliateCode()))
                .active(user.isAffiliate())
                .build();
    }

    /**
     * Generate a unique 8-character uppercase alphanumeric code.
     * Retries until a code not already in use is found (collision is astronomically
     * unlikely with 36^8 ≈ 2.8 trillion combinations, but we guard anyway).
     */
    private String generateUniqueCode() {
        String candidate;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            candidate = sb.toString();
        } while (userRepository.existsByAffiliateCode(candidate));
        return candidate;
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private AffiliateCommissionResponse toCommissionResponse(CommissionTransaction ct) {
        Order order = ct.getOrder();
        return AffiliateCommissionResponse.builder()
                .id(ct.getId())
                .orderId(order.getId())
                .orderNetwork(order.getNetwork().name())
                .orderCapacityGb(order.getCapacityGb())
                .referredUserMasked(maskName(ct.getReferredUser().getFullName()))
                .commissionGhc(ct.getCommissionGhc())
                .reversed(ct.isReversed())
                .reversedAt(ct.getReversedAt())
                .createdAt(ct.getCreatedAt())
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

    private String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Unknown";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0];
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }
}