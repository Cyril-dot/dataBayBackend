package com.databundleHum.OnetBundleHub.controllers;

import com.databundleHum.OnetBundleHub.dtos.PricingResponse;
import com.databundleHum.OnetBundleHub.security.UserPrincipal;
import com.databundleHum.OnetBundleHub.services.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return principal.userId();
    }

    /**
     * GET /api/v1/pricing/public — no auth required. Admin's public price
     * for every active bundle, no reseller-override resolution. This is
     * the ONLY pricing route safe to call before login, since it never
     * exposes Big Dreams' buying price or any reseller's custom pricing.
     */
    @GetMapping("/public")
    public ResponseEntity<List<PricingResponse>> getPublicPricing() {
        log.debug("[PRICING] Public pricing requested");
        return ResponseEntity.ok(pricingService.getPublicPricing());
    }

    /**
     * GET /api/v1/pricing/store/{storeSlug} — no auth required. The
     * effective pricing table a BUYER visiting this reseller's storefront
     * would see: the reseller's custom prices where set, admin PUBLIC price
     * as fallback everywhere else. Safe to expose pre-login for the same
     * reason /public is — it never reveals wholesale/cost pricing, only
     * what a buyer would pay. Resolves the reseller by their public
     * storeSlug rather than requiring a resellerId, since a storefront
     * visitor only ever has the slug from the URL.
     */
    @GetMapping("/store/{storeSlug}")
    public ResponseEntity<List<PricingResponse>> getPricingByStoreSlug(@PathVariable String storeSlug) {
        log.debug("[PRICING] Storefront pricing requested: storeSlug={}", storeSlug);
        List<PricingResponse> response = pricingService.getPricingForResellerBySlug(storeSlug);
        log.debug("[PRICING] Storefront pricing resolved: storeSlug={} rows={}", storeSlug, response.size());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/pricing/effective — any authenticated user. What THIS
     * user pays as a BUYER: their referring reseller's custom prices where
     * set, admin PUBLIC price as fallback. Deliberately shared across all
     * roles (a reseller can also buy as a customer under this same rule),
     * so this route must NOT be repointed at reseller cost pricing — see
     * /reseller/cost below for that.
     */
    @GetMapping("/effective")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PricingResponse>> getEffectivePricing() {
        UUID userId = currentUserId();
        log.debug("[PRICING] Effective pricing requested: userId={}", userId);
        List<PricingResponse> response = pricingService.getEffectivePricingForUser(userId);
        log.debug("[PRICING] Effective pricing resolved: userId={} rows={}", userId, response.size());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/pricing/reseller/effective — reseller-only. Lets the
     * logged-in reseller preview their OWN effective pricing table: their
     * custom ResellerPricing rows where set, admin public price as fallback
     * everywhere else — i.e. exactly what a buyer they referred would see.
     * Each row's isCustomPrice flag tells the reseller's dashboard which
     * bundles are their own price vs. still on the admin default.
     */
    @GetMapping("/reseller/effective")
    @PreAuthorize("hasRole('RESELLER')")
    public ResponseEntity<List<PricingResponse>> getResellerPricing() {
        UUID resellerId = currentUserId();
        log.debug("[PRICING] Reseller pricing requested: resellerId={}", resellerId);
        List<PricingResponse> response = pricingService.getPricingForReseller(resellerId);
        log.debug("[PRICING] Reseller pricing resolved: resellerId={} rows={}", resellerId, response.size());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/pricing/reseller/cost — reseller-only. What THIS reseller
     * pays as their own wholesale cost for every active bundle — the floor
     * their own sellingPriceGhc must sit above when setting prices on
     * /reseller/effective.
     *
     * Distinct from /reseller/effective: that route answers "what would a
     * buyer I referred see?" (falls back to admin PUBLIC price); this route
     * answers "what do I pay?" (falls back to admin WHOLESALE reseller
     * price, or the upstream reseller's price if this reseller was itself
     * referred). Used by the "Add or update a price" form's cost banner so
     * the margin check validates against the right number.
     */
    @GetMapping("/reseller/cost")
    @PreAuthorize("hasRole('RESELLER')")
    public ResponseEntity<List<PricingResponse>> getResellerCostPricing() {
        UUID resellerId = currentUserId();
        log.debug("[PRICING] Reseller cost pricing requested: resellerId={}", resellerId);
        List<PricingResponse> response = pricingService.getCostPricingForReseller(resellerId);
        log.debug("[PRICING] Reseller cost pricing resolved: resellerId={} rows={}", resellerId, response.size());
        return ResponseEntity.ok(response);
    }
}