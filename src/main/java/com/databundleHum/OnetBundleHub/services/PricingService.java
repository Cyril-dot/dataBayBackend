package com.databundleHum.OnetBundleHub.services;


import com.databundleHum.OnetBundleHub.dtos.PricingResponse;
import com.databundleHum.OnetBundleHub.entity.PlatformSettings;
import com.databundleHum.OnetBundleHub.entity.ResellerPricing;
import com.databundleHum.OnetBundleHub.entity.User;
import com.databundleHum.OnetBundleHub.repos.PlatformSettingsRepository;
import com.databundleHum.OnetBundleHub.repos.ResellerPricingRepository;
import com.databundleHum.OnetBundleHub.repos.UserRepository;
import com.databundleHum.OnetBundleHub.security.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Resolves the price a given user should see/pay for every active bundle,
 * taking reseller-referral attribution AND the caller's own role into account.
 *
 * Rule:
 *   - If User.referredByReseller is set (populated at registration in
 *     AuthService.register() from the ref_reseller_id cookie), the user sees
 *     THAT reseller's custom ResellerPricing rows wherever the reseller has
 *     set one, falling back to admin's public price for anything the
 *     referring reseller hasn't custom-priced.
 *   - If the user has NO referring reseller AND is a RESELLER themselves
 *     (i.e. a reseller who signed up directly, not via another reseller's
 *     link), they buy from the admin at the admin's reseller-tier cost
 *     price (PlatformSettings.resellerPriceGhc) — never the public price.
 *   - If the user has no referring reseller and is a plain USER (or ADMIN),
 *     they get the admin's public pricing table outright.
 *
 * This is deliberately separate from ResellerStorefrontService, which
 * resolves pricing by store SLUG (visiting a reseller's storefront URL
 * directly). This service resolves pricing by the BUYER's own account-level
 * referral attribution and role, so a user referred by reseller X sees X's
 * prices here even on the generic "Buy a bundle" page, not only on X's
 * storefront — and a direct reseller sees their own cost price everywhere,
 * not the customer-facing price.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

    private final UserRepository             userRepository;
    private final PlatformSettingsRepository platformSettingsRepository;
    private final ResellerPricingRepository  resellerPricingRepository;

    @Transactional(readOnly = true)
    public List<PricingResponse> getEffectivePricingForUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        List<PlatformSettings> activeSettings = platformSettingsRepository.findByActiveTrue();
        User referringReseller = user.getReferredByReseller();

        if (referringReseller == null) {
            if (user.getRole() == User.Role.RESELLER) {
                // A direct reseller (no referring reseller of their own) buys
                // from the admin at the reseller-tier cost price, not the
                // public/customer-facing price.
                log.debug("[PRICING] userId={} is a direct reseller — admin reseller-cost pricing", userId);
                return activeSettings.stream().map(this::toResellerCostPriceResponse).toList();
            }
            log.debug("[PRICING] userId={} has no referring reseller — admin public pricing", userId);
            return activeSettings.stream().map(this::toPublicPriceResponse).toList();
        }

        Map<String, ResellerPricing> overrides = new HashMap<>();
        for (ResellerPricing rp : resellerPricingRepository.findByReseller(referringReseller)) {
            overrides.put(key(rp.getNetwork().name(), rp.getCapacityGb()), rp);
        }

        List<PricingResponse> result = new ArrayList<>();
        for (PlatformSettings settings : activeSettings) {
            ResellerPricing override = overrides.get(
                    key(settings.getNetwork().name(), settings.getCapacityGb()));

            result.add(override != null
                    ? PricingResponse.builder()
                    .network(settings.getNetwork().name())
                    .capacityGb(settings.getCapacityGb())
                    .publicPriceGhc(override.getSellingPriceGhc())
                    .build()
                    : toPublicPriceResponse(settings));
        }

        log.debug("[PRICING] userId={} referredByResellerId={} — {} row(s), {} override(s)",
                userId, referringReseller.getId(), result.size(), overrides.size());

        return result;
    }

    private PricingResponse toPublicPriceResponse(PlatformSettings s) {
        return PricingResponse.builder()
                .network(s.getNetwork().name())
                .capacityGb(s.getCapacityGb())
                .publicPriceGhc(s.getPublicPriceGhc())
                .build();
    }

    /**
     * Same shape as toPublicPriceResponse, but carries the admin's
     * reseller-tier cost price instead of the public price. Used for
     * resellers who have no referring reseller of their own (i.e. they
     * buy directly from the admin). The DTO field is still named
     * publicPriceGhc — it's a generic "resolved price" carrier reused
     * across guest/user/reseller contexts — so callers should treat it
     * as "the price this caller pays," not literally "the public price."
     */
    private PricingResponse toResellerCostPriceResponse(PlatformSettings s) {
        return PricingResponse.builder()
                .network(s.getNetwork().name())
                .capacityGb(s.getCapacityGb())
                .publicPriceGhc(s.getResellerPriceGhc())
                .build();
    }

    /**
     * Resolves the price a single user pays for a single bundle — same rule as
     * getEffectivePricingForUser, but a single lookup instead of building the
     * whole catalog. Used by OrderService when placing an actual order, where
     * we already have the User and PlatformSettings in hand.
     */
    @Transactional(readOnly = true)
    public BigDecimal resolvePriceForUser(User user, PlatformSettings settings) {
        User referringReseller = user.getReferredByReseller();

        if (referringReseller == null) {
            if (user.getRole() == User.Role.RESELLER) {
                // Direct reseller, no referrer of their own — admin's
                // reseller-tier cost price, not the public price.
                return settings.getResellerPriceGhc();
            }
            return settings.getPublicPriceGhc();
        }

        return resellerPricingRepository
                .findByResellerAndNetworkAndCapacityGb(
                        referringReseller, settings.getNetwork(), settings.getCapacityGb())
                .map(ResellerPricing::getSellingPriceGhc)
                .orElseGet(() -> {
                    log.debug("[PRICING] resellerId={} has no override for network={} gb={} — admin fallback",
                            referringReseller.getId(), settings.getNetwork(), settings.getCapacityGb());
                    return settings.getPublicPriceGhc();
                });
    }

    private String key(String network, BigDecimal capacityGb) {
        return network + ":" + capacityGb.stripTrailingZeros().toPlainString();
    }

    /**
     * Public, unauthenticated pricing table — admin's publicPriceGhc for every
     * active bundle, no reseller-override resolution (there's no account to
     * resolve from). Used by the guest-facing bundle picker before login/signup.
     */
    @Transactional(readOnly = true)
    public List<PricingResponse> getPublicPricing() {
        return platformSettingsRepository.findByActiveTrue().stream()
                .map(this::toPublicPriceResponse)
                .toList();
    }
}