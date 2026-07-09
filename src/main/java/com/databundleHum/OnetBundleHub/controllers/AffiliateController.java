package com.databundleHum.OnetBundleHub.controllers;

import com.databundleHum.OnetBundleHub.dtos.AffiliateActivateResponse;
import com.databundleHum.OnetBundleHub.dtos.AffiliateCommissionResponse;
import com.databundleHum.OnetBundleHub.dtos.AffiliateDashboardResponse;
import com.databundleHum.OnetBundleHub.security.UserPrincipal;
import com.databundleHum.OnetBundleHub.services.AffiliateService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST endpoints for the affiliate programme.
 *
 * Base path: /api/v1/affiliate
 *
 * All endpoints require a valid JWT (any role — USER, RESELLER, ADMIN).
 * The user ID is extracted from the JWT principal via SecurityContextHolder,
 * matching the pattern used in OrderController.
 *
 * Endpoints:
 *   POST   /api/v1/affiliate/activate    — activate (or re-activate) affiliate status
 *   DELETE /api/v1/affiliate/deactivate  — deactivate (code is retained)
 *   GET    /api/v1/affiliate/dashboard   — stats: referrals, earnings, wallet
 *   GET    /api/v1/affiliate/commissions — paginated commission history
 *
 * The /a/{affiliateCode} redirect is handled by AffiliateRedirectController
 * (sets a cookie and redirects to the homepage — no auth required).
 */
@RestController
@RequestMapping("/api/v1/affiliate")
@RequiredArgsConstructor
public class AffiliateController {

    private final AffiliateService affiliateService;

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return principal.userId();
    }

    /**
     * Activate the affiliate programme for the authenticated user.
     *
     * Idempotent: calling this when already active returns the current state
     * without error.
     *
     * Response: { affiliateCode, referralUrl, active }
     */
    @PostMapping("/activate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AffiliateActivateResponse> activate() {
        return ResponseEntity.ok(affiliateService.activate(currentUserId()));
    }

    /**
     * Deactivate the affiliate programme for the authenticated user.
     *
     * The affiliate code is retained in the DB. The /a/{code} redirect will
     * return a graceful "programme inactive" page. Already-earned commissions
     * are not affected.
     */
    @DeleteMapping("/deactivate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deactivate() {
        affiliateService.deactivate(currentUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Fetch the affiliate dashboard summary.
     *
     * Returns referral link, total sign-ups, referred users with orders,
     * total commission earned, this month's commission, and wallet balance.
     *
     * Requires: user.isAffiliate == true (throws 403 if not).
     */
    @GetMapping("/dashboard")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AffiliateDashboardResponse> getDashboard() {
        return ResponseEntity.ok(affiliateService.getDashboard(currentUserId()));
    }

    /**
     * Paginated commission history for the affiliate dashboard table.
     *
     * Default page size: 20, sorted newest-first.
     *
     * Requires: user.isAffiliate == true (throws 403 if not).
     */
    @GetMapping("/commissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<AffiliateCommissionResponse>> getCommissionHistory(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(affiliateService.getCommissionHistory(currentUserId(), pageable));
    }
}