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
}