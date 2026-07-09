package com.databundleHum.OnetBundleHub.services;

import com.databundleHum.OnetBundleHub.config.AppConfig;
import com.databundleHum.OnetBundleHub.dtos.InitiateGuestStorefrontOrderRequest;
import com.databundleHum.OnetBundleHub.dtos.StorefrontResponse;
import com.databundleHum.OnetBundleHub.dtos.WalletOrderRequest;
import com.databundleHum.OnetBundleHub.dtos.response.OrderResponse;
import com.databundleHum.OnetBundleHub.entity.*;
import com.databundleHum.OnetBundleHub.entity.WalletTransaction.TransactionType;
import com.databundleHum.OnetBundleHub.repos.*;
import com.databundleHum.OnetBundleHub.security.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles all interactions with a reseller's public storefront.
 *
 * Public endpoint — no authentication required for browsing or guest checkout.
 * Wallet checkout requires the customer to be logged in (standard JWT auth).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResellerStorefrontService {

    private static final int DUPLICATE_WINDOW_SECONDS = 30;

    private final ResellerProfileRepository  resellerProfileRepository;
    private final ResellerPricingRepository  resellerPricingRepository;
    private final PlatformSettingsRepository platformSettingsRepository;
    private final OrderRepository            orderRepository;
    private final UserRepository             userRepository;
    private final WalletService              walletService;
    private final PaystackService            paystackService;
    private final BigDreamsService           bigDreamsService;
    private final NotificationService        notificationService;
    private final AppConfig appConfig;

    // ── Storefront browse ─────────────────────────────────────────────────────

    /**
     * Fetch the storefront display data for a given slug.
     *
     * Returns all branding/customization fields plus the list of bundles the
     * reseller has priced at their custom selling prices.
     * Only bundles that are active in platform_settings AND have a reseller
     * pricing row are shown.
     *
     * @param slug  the reseller's store slug, e.g. "kwame-data"
     * @return      storefront data including bundle list
     */
    @Transactional(readOnly = true)
    public StorefrontResponse getStorefront(String slug) {
        ResellerProfile profile = findProfileBySlugOrThrow(slug);

        if (profile.getStatus() != ResellerProfile.ResellerStatus.APPROVED) {
            throw new ResourceNotFoundException("Store not found: " + slug);
        }

        List<ResellerPricing> pricingRows = resellerPricingRepository
                .findByResellerWithActivePlatformSettings(profile.getUser());

        List<StorefrontResponse.BundleItem> bundles = pricingRows.stream()
                .map(p -> StorefrontResponse.BundleItem.builder()
                        .network(p.getNetwork().name())
                        .capacityGb(p.getCapacityGb())
                        .sellingPriceGhc(p.getSellingPriceGhc())
                        .build())
                .collect(Collectors.toList());

        log.debug("[STOREFRONT] Fetched store: slug={} bundleCount={}", slug, bundles.size());

        return StorefrontResponse.builder()
                .storeSlug(profile.getStoreSlug())
                .storeName(profile.getEffectiveStoreName())
                .storeTagline(profile.getStoreTagline())
                .storeLogoUrl(profile.getStoreLogoUrl())
                .themeColour(profile.getThemeColour())
                // New customization fields
                .whatsappNumber(profile.getWhatsappNumber())
                .instagramHandle(profile.getInstagramHandle())
                .bannerImageUrl(profile.getBannerImageUrl())
                .welcomeMessage(profile.getWelcomeMessage())
                .buttonStyle(profile.getButtonStyle() != null
                        ? profile.getButtonStyle().name() : null)
                .storeTheme(profile.getStoreTheme() != null
                        ? profile.getStoreTheme().name() : null)
                .bundles(bundles)
                .build();
    }

    // ── Guest storefront order ────────────────────────────────────────────────

    /**
     * Initiate a Paystack-backed guest order through a reseller's storefront.
     */
    @Transactional
    public OrderResponse initiateGuestStorefrontOrder(String slug,
                                                      InitiateGuestStorefrontOrderRequest request) {
        ResellerProfile profile   = findApprovedProfileBySlugOrThrow(slug);
        ResellerPricing pricing   = findResellerPricingOrThrow(profile.getUser(),
                request.getNetwork(), request.getCapacityGb());
        PlatformSettings settings = findActiveSettingsOrThrow(request.getNetwork(),
                request.getCapacityGb());

        BigDecimal sellingPrice = pricing.getSellingPriceGhc();
        BigDecimal costPrice    = settings.getResellerPriceGhc();

        String reference  = paystackService.generateReference();
        String guestEmail = "guest@" + appConfig.getAppBaseUrl()
                .replaceAll("https?://", "");

        paystackService.initiateTransaction(
                guestEmail,
                sellingPrice,
                reference,
                Map.of(
                        "type",              "STOREFRONT_GUEST_ORDER",
                        "phone",             request.getPhoneNumber(),
                        "network",           request.getNetwork().name(),
                        "capacityGb",        request.getCapacityGb().toString(),
                        "resellerProfileId", profile.getId().toString()
                )
        );

        Order order = Order.builder()
                .phoneNumber(request.getPhoneNumber())
                .network(request.getNetwork())
                .capacityGb(request.getCapacityGb())
                .costPriceGhc(costPrice)
                .sellingPriceGhc(sellingPrice)
                .paymentMethod(Order.PaymentMethod.PAYSTACK)
                .paystackRef(reference)
                .status(Order.OrderStatus.PENDING)
                .guest(true)
                .orderedByRole(Order.OrderedByRole.RESELLER)
                .resellerProfile(profile)
                .storefrontOrder(true)
                .build();

        orderRepository.save(order);

        log.info("[STOREFRONT] Guest order initiated: slug={} orderId={} ref={} phone={} network={} gb={} price={}",
                slug, order.getId(), reference, request.getPhoneNumber(),
                request.getNetwork(), request.getCapacityGb(), sellingPrice);

        return toOrderResponse(order);
    }

    /**
     * Called by WebhookController after Paystack HMAC validation.
     * Idempotent — duplicate references are silently ignored.
     */
    @Transactional
    public void fulfilStorefrontPaystackOrder(String paystackRef) {
        Order order = orderRepository.findByPaystackRef(paystackRef)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found for Paystack ref: " + paystackRef));

        order.setStatus(Order.OrderStatus.VERIFIED);
        orderRepository.save(order);

        log.info("[STOREFRONT] Paystack order VERIFIED: orderId={} ref={}", order.getId(), paystackRef);

        try {
            bigDreamsService.purchase(order);
            updateResellerStats(order);
        } catch (UpstreamApiException ex) {
            log.error("[STOREFRONT] Bundle provision failed: orderId={} error={}", order.getId(), ex.getMessage());
            if (order.getUser() != null) {
                notificationService.sendOrderFailedAlert(
                        order.getUser().getEmail(), order.getUser().getFullName(), order.getId());
            }
        }
    }

    // ── Wallet storefront order ───────────────────────────────────────────────

    /**
     * Place a wallet-funded order through a reseller's storefront.
     */
    @Transactional
    public OrderResponse placeWalletStorefrontOrder(String slug, UUID customerId,
                                                    WalletOrderRequest request) {
        User            customer  = findUserOrThrow(customerId);
        ResellerProfile profile   = findApprovedProfileBySlugOrThrow(slug);
        ResellerPricing pricing   = findResellerPricingOrThrow(profile.getUser(),
                request.getNetwork(), request.getCapacityGb());
        PlatformSettings settings = findActiveSettingsOrThrow(request.getNetwork(),
                request.getCapacityGb());

        BigDecimal sellingPrice = pricing.getSellingPriceGhc();
        BigDecimal costPrice    = settings.getResellerPriceGhc();

        walletService.debit(customerId, sellingPrice, TransactionType.PURCHASE,
                "Data bundle " + request.getCapacityGb() + "GB " + request.getNetwork()
                        + " via " + profile.getEffectiveStoreName(),
                null);

        Order order = Order.builder()
                .user(customer)
                .phoneNumber(request.getPhoneNumber())
                .network(request.getNetwork())
                .capacityGb(request.getCapacityGb())
                .costPriceGhc(costPrice)
                .sellingPriceGhc(sellingPrice)
                .paymentMethod(Order.PaymentMethod.WALLET)
                .status(Order.OrderStatus.PENDING)
                .guest(false)
                .orderedByRole(Order.OrderedByRole.RESELLER)
                .resellerProfile(profile)
                .storefrontOrder(true)
                .build();

        try {
            order = orderRepository.save(order);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateOrderException(
                    "A similar order was already placed in the last "
                            + DUPLICATE_WINDOW_SECONDS + " seconds.");
        }

        log.info("[STOREFRONT] Wallet order placed: slug={} customerId={} orderId={} price={}",
                slug, customerId, order.getId(), sellingPrice);

        try {
            bigDreamsService.purchase(order);
            updateResellerStats(order);
        } catch (UpstreamApiException ex) {
            log.error("[STOREFRONT] Bundle provision failed: orderId={} error={}",
                    order.getId(), ex.getMessage());

            walletService.credit(customerId, sellingPrice, TransactionType.REFUND,
                    "Refund: failed bundle delivery for order #" + order.getId(), null);

            notificationService.sendOrderFailedAlert(
                    customer.getEmail(), customer.getFullName(), order.getId());
        }

        return toOrderResponse(orderRepository.save(order));
    }

    // ── Reseller stats update ─────────────────────────────────────────────────

    @Transactional
    public void updateResellerStats(Order order) {
        if (order.getResellerProfile() == null) return;

        ResellerProfile profile = resellerProfileRepository
                .findById(order.getResellerProfile().getId())
                .orElse(null);

        if (profile == null) return;

        BigDecimal revenue = order.getSellingPriceGhc();
        BigDecimal cost    = order.getCostPriceGhc();
        BigDecimal profit  = revenue.subtract(cost);

        profile.setTotalRevenueGhc(profile.getTotalRevenueGhc().add(revenue));
        profile.setTotalCostGhc(profile.getTotalCostGhc().add(cost));
        profile.setTotalProfitGhc(profile.getTotalProfitGhc().add(profit));

        resellerProfileRepository.save(profile);

        log.info("[STOREFRONT] Reseller stats updated: profileId={} +revenue={} +cost={} +profit={}",
                profile.getId(), revenue, cost, profit);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ResellerProfile findProfileBySlugOrThrow(String slug) {
        return resellerProfileRepository.findByStoreSlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + slug));
    }

    private ResellerProfile findApprovedProfileBySlugOrThrow(String slug) {
        ResellerProfile profile = findProfileBySlugOrThrow(slug);
        if (profile.getStatus() != ResellerProfile.ResellerStatus.APPROVED) {
            throw new ResourceNotFoundException("Store not found: " + slug);
        }
        return profile;
    }

    private ResellerPricing findResellerPricingOrThrow(User reseller,
                                                        PlatformSettings.Network network,
                                                        BigDecimal capacityGb) {
        return resellerPricingRepository
                .findByResellerAndNetworkAndCapacityGb(reseller, network, capacityGb)
                .orElseThrow(() -> new BundleNotFoundException(
                        "Bundle not available on this store: network=" + network
                                + " capacityGb=" + capacityGb));
    }

    private PlatformSettings findActiveSettingsOrThrow(PlatformSettings.Network network,
                                                       BigDecimal capacityGb) {
        return platformSettingsRepository
                .findByNetworkAndCapacityGbAndActiveTrue(network, capacityGb)
                .orElseThrow(() -> new BundleNotFoundException(
                        "Bundle not available: network=" + network
                                + " capacityGb=" + capacityGb));
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
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
}