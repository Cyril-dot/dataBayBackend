package com.databundleHum.OnetBundleHub.services;


import com.databundleHum.OnetBundleHub.dtos.PricingResponse;
import com.databundleHum.OnetBundleHub.entity.PlatformSettings;
import com.databundleHum.OnetBundleHub.entity.ResellerPricing;
import com.databundleHum.OnetBundleHub.entity.ResellerProfile;
import com.databundleHum.OnetBundleHub.entity.User;
import com.databundleHum.OnetBundleHub.repos.PlatformSettingsRepository;
import com.databundleHum.OnetBundleHub.repos.ResellerPricingRepository;
import com.databundleHum.OnetBundleHub.repos.ResellerProfileRepository;
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
 * There are TWO distinct questions this service answers, and they must not
 * be conflated:
 *
 *   1) "What does a BUYER pay?" (getEffectivePricingForUser / resolvePriceForUser /
 *      getPricingForReseller / getPricingForResellerBySlug as a preview of
 *      what a referred buyer would see)
 *        - If User.referredByReseller is set, the buyer sees THAT reseller's
 *          custom ResellerPricing rows wherever set, falling back to the
 *          admin's PUBLIC retail price (PlatformSettings.publicPriceGhc)
 *          wherever the reseller hasn't custom-priced a bundle.
 *        - If the user has no referring reseller, they get the admin's
 *          public pricing table outright.
 *
 *   2) "What does a RESELLER pay as their own wholesale cost?"
 *      (getCostPricingForReseller)
 *        - If the reseller has no referring reseller (sourced directly from
 *          admin), their cost is the admin's WHOLESALE reseller price
 *          (PlatformSettings.resellerPriceGhc) — NOT the public retail price.
 *        - If the reseller WAS referred by another reseller, their cost is
 *          that upstream reseller's sellingPriceGhc where set, falling back
 *          to the admin's wholesale resellerPriceGhc (still not the public
 *          price) wherever the upstream reseller hasn't custom-priced a
 *          bundle.
 *
 * These two questions share a similar "override then fallback" shape, so the
 * override-resolution logic is shared via buildPricingWithOverrides(), but
 * the two fallback fields (publicPriceGhc vs resellerPriceGhc) must never be
 * swapped — doing so would let a reseller's selling-price floor be validated
 * against the wrong number.
 *
 * This is deliberately separate from ResellerStorefrontService, which
 * resolves pricing by store SLUG (visiting a reseller's storefront URL
 * directly). This service resolves pricing by the BUYER's own account-level
 * referral attribution, so a user referred by reseller X sees X's prices
 * here even on the generic "Buy a bundle" page, not only on X's storefront.
 * getPricingForResellerBySlug() is the one exception — a slug-based lookup
 * added here to support storefront pages needing the buyer-facing preview
 * without already holding a resellerId.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

    private final UserRepository             userRepository;
    private final PlatformSettingsRepository platformSettingsRepository;
    private final ResellerPricingRepository  resellerPricingRepository;
    private final ResellerProfileRepository  resellerProfileRepository;

    // ── Buyer-facing pricing ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PricingResponse> getEffectivePricingForUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        User referringReseller = user.getReferredByReseller();

        if (referringReseller == null) {
            log.debug("[PRICING] userId={} has no referring reseller — admin public pricing", userId);
            return getPublicPricing();
        }

        List<PricingResponse> result = buildPricingWithOverrides(referringReseller, false);

        log.debug("[PRICING] userId={} referredByResellerId={} — {} row(s)",
                userId, referringReseller.getId(), result.size());

        return result;
    }

    /**
     * Lets a reseller preview their own effective pricing table — the exact
     * pricing a BUYER referred by them would see. Bundles the reseller hasn't
     * custom-priced fall back to the admin's PUBLIC price, same as any buyer,
     * but each row is flagged via isCustomPrice so the reseller's dashboard
     * can distinguish "my price" from "admin fallback" at a glance.
     *
     * NOTE: this is a buyer-facing preview, not the reseller's own cost. Use
     * getCostPricingForReseller() for what the reseller themselves pays.
     */
    @Transactional(readOnly = true)
    public List<PricingResponse> getPricingForReseller(UUID resellerId) {
        User reseller = userRepository.findById(resellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Reseller not found: " + resellerId));

        List<PricingResponse> result = buildPricingWithOverrides(reseller, false);

        log.debug("[PRICING] resellerId={} viewing own storefront-preview pricing — {} row(s)",
                resellerId, result.size());

        return result;
    }

    /**
     * Same as {@link #getPricingForReseller(UUID)} — the effective catalog a
     * BUYER referred by this reseller would see (custom price where set,
     * admin PUBLIC price as fallback) — but resolves the reseller by their
     * public storeSlug instead of an internal userId. Used by the storefront
     * page (/store/{slug}) before/without a resellerId being known.
     */
    @Transactional(readOnly = true)
    public List<PricingResponse> getPricingForResellerBySlug(String storeSlug) {
        ResellerProfile profile = resellerProfileRepository.findByStoreSlug(storeSlug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No reseller found for storeSlug: " + storeSlug));

        List<PricingResponse> result = buildPricingWithOverrides(profile.getUser(), false);

        log.debug("[PRICING] storeSlug={} resellerId={} — {} row(s)",
                storeSlug, profile.getUser().getId(), result.size());

        return result;
    }

    // ── Reseller cost pricing ────────────────────────────────────────────────

    /**
     * Resolves what THIS reseller pays as their own wholesale cost — the
     * floor their sellingPriceGhc must sit above. Distinct from
     * getPricingForReseller(), which resolves what a BUYER under this
     * reseller would pay.
     *
     * Rule:
     *   - If the reseller has no referring reseller (sourced directly from
     *     admin), their cost for every active bundle is the admin's
     *     wholesale resellerPriceGhc.
     *   - If the reseller WAS referred by another reseller, their cost is
     *     that upstream reseller's sellingPriceGhc where custom-priced,
     *     falling back to the admin's wholesale resellerPriceGhc (never the
     *     public retail price) for any bundle the upstream reseller hasn't
     *     priced.
     */
    @Transactional(readOnly = true)
    public List<PricingResponse> getCostPricingForReseller(UUID resellerId) {
        User reseller = userRepository.findById(resellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Reseller not found: " + resellerId));

        User referringReseller = reseller.getReferredByReseller();

        if (referringReseller == null) {
            log.debug("[PRICING] resellerId={} sources directly from admin — wholesale reseller pricing", resellerId);
            return getAdminResellerPricing();
        }

        List<PricingResponse> result = buildPricingWithOverrides(referringReseller, true);

        log.debug("[PRICING] resellerId={} cost priced from referringResellerId={} — {} row(s)",
                resellerId, referringReseller.getId(), result.size());

        return result;
    }

    /**
     * Resolves the cost a single reseller pays for a single bundle — same
     * rule as getCostPricingForReseller, but a single lookup instead of
     * building the whole catalog.
     */
    @Transactional(readOnly = true)
    public BigDecimal resolveCostForReseller(User reseller, PlatformSettings settings) {
        User referringReseller = reseller.getReferredByReseller();

        if (referringReseller == null) {
            return settings.getResellerPriceGhc();
        }

        return resellerPricingRepository
                .findByResellerAndNetworkAndCapacityGb(
                        referringReseller, settings.getNetwork(), settings.getCapacityGb())
                .map(ResellerPricing::getSellingPriceGhc)
                .orElseGet(() -> {
                    log.debug("[PRICING] referringResellerId={} has no override for network={} gb={} — admin wholesale fallback",
                            referringReseller.getId(), settings.getNetwork(), settings.getCapacityGb());
                    return settings.getResellerPriceGhc();
                });
    }

    /**
     * Admin's wholesale reseller pricing table — PlatformSettings.resellerPriceGhc
     * for every active bundle. This is the cost floor for resellers sourcing
     * directly from admin (no referring reseller).
     */
    @Transactional(readOnly = true)
    public List<PricingResponse> getAdminResellerPricing() {
        return platformSettingsRepository.findByActiveTrue().stream()
                .map(this::toAdminResellerPriceResponse)
                .toList();
    }

    // ── Shared builder ────────────────────────────────────────────────────────

    /**
     * Shared builder: every active bundle, priced from `reseller`'s
     * ResellerPricing rows where one exists. isCustomPrice reflects which
     * branch was taken for that row.
     *
     * @param costFallback  when false (buyer-facing), bundles the reseller
     *                      hasn't custom-priced fall back to the admin's
     *                      PUBLIC retail price. When true (cost-facing),
     *                      they fall back to the admin's WHOLESALE reseller
     *                      price instead. These must never be swapped.
     */
    private List<PricingResponse> buildPricingWithOverrides(User reseller, boolean costFallback) {
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
                    : (costFallback
                       ? toAdminResellerPriceResponse(settings)
                       : toPublicPriceResponse(settings)));
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
     * Maps a PlatformSettings row to a PricingResponse carrying the admin's
     * WHOLESALE reseller price in the publicPriceGhc field. Field name is
     * kept as publicPriceGhc for DTO-shape compatibility with the buyer-facing
     * responses (same shape, different fallback source) — callers reading
     * this from a cost-pricing endpoint should treat it as "the price to
     * display", not literally "the admin's public retail price".
     */
    private PricingResponse toAdminResellerPriceResponse(PlatformSettings s) {
        return PricingResponse.builder()
                .network(s.getNetwork().name())
                .capacityGb(s.getCapacityGb())
                .publicPriceGhc(s.getResellerPriceGhc())
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