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
 * taking reseller-referral attribution into account.
 *
 * Rule:
 *   - If User.referredByReseller is set (populated at registration in
 *     AuthService.register() from the ref_reseller_id cookie), the user sees
 *     THAT reseller's custom ResellerPricing rows wherever the reseller has
 *     set one.
 *   - For any active bundle the referring reseller has NOT custom-priced,
 *     fall back to the admin's public price (PlatformSettings.publicPriceGhc)
 *     so the picker never shows a gap.
 *   - If the user has no referring reseller, they get the admin's public
 *     pricing table outright.
 *
 * This is deliberately separate from ResellerStorefrontService, which
 * resolves pricing by store SLUG (visiting a reseller's storefront URL
 * directly). This service resolves pricing by the BUYER's own account-level
 * referral attribution, so a user referred by reseller X sees X's prices
 * here even on the generic "Buy a bundle" page, not only on X's storefront.
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

        User referringReseller = user.getReferredByReseller();

        if (referringReseller == null) {
            log.debug("[PRICING] userId={} has no referring reseller — admin public pricing", userId);
            return getPublicPricing();
        }

        List<PricingResponse> result = buildPricingWithOverrides(referringReseller);

        log.debug("[PRICING] userId={} referredByResellerId={} — {} row(s)",
                userId, referringReseller.getId(), result.size());

        return result;
    }

    /**
     * Lets a reseller preview their own effective pricing table — the exact
     * pricing a buyer referred by them would see. Bundles the reseller hasn't
     * custom-priced fall back to the admin's public price, same as any buyer,
     * but each row is flagged via isCustomPrice so the reseller's dashboard
     * can distinguish "my price" from "admin fallback" at a glance.
     */
    @Transactional(readOnly = true)
    public List<PricingResponse> getPricingForReseller(UUID resellerId) {
        User reseller = userRepository.findById(resellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Reseller not found: " + resellerId));

        List<PricingResponse> result = buildPricingWithOverrides(reseller);

        log.debug("[PRICING] resellerId={} viewing own pricing — {} row(s)",
                resellerId, result.size());

        return result;
    }

    /**
     * Shared builder: every active bundle, priced from `reseller`'s
     * ResellerPricing rows where one exists, admin public price otherwise.
     * isCustomPrice reflects which branch was taken for that row.
     */
    private List<PricingResponse> buildPricingWithOverrides(User reseller) {
        List<PlatformSettings> activeSettings = platformSettingsRepository.findByActiveTrue();

        Map<String, ResellerPricing> overrides = new HashMap<>();
        for (ResellerPricing rp : resellerPricingRepository.findByReseller(reseller)) {
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
                    .isCustomPrice(true)
                    .build()
                    : toPublicPriceResponse(settings));
        }

        return result;
    }

    private PricingResponse toPublicPriceResponse(PlatformSettings s) {
        return PricingResponse.builder()
                .network(s.getNetwork().name())
                .capacityGb(s.getCapacityGb())
                .publicPriceGhc(s.getPublicPriceGhc())
                .isCustomPrice(false)
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