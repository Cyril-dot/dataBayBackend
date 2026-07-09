package com.databundleHum.OnetBundleHub.controllers;

import com.databundleHum.OnetBundleHub.dtos.*;
import com.databundleHum.OnetBundleHub.dtos.response.*;
import com.databundleHum.OnetBundleHub.dtos.response.ResellerDashboardResponse;
import com.databundleHum.OnetBundleHub.security.UserPrincipal;
import com.databundleHum.OnetBundleHub.services.ResellerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Reseller controller.
 *
 * <p>Apply endpoint is accessible to any authenticated user (USER role).
 * All other endpoints require {@code ROLE_RESELLER}.
 *
 * <p>Base path: {@code /api/v1/reseller}
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/reseller")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reseller", description = "Reseller application, pricing, orders, and payouts")
public class ResellerController {

    private final ResellerService resellerService;

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return principal.userId();
    }

    // ── Application & Profile ─────────────────────────────────────────────────

    @PostMapping("/apply")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Apply to become a reseller (deducts GHS 20 registration fee from wallet)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Application submitted, pending admin review"),
            @ApiResponse(responseCode = "400", description = "Insufficient wallet balance"),
            @ApiResponse(responseCode = "409", description = "Application already exists or already a reseller")
    })
    public ResponseEntity<ResellerApplicationResponse> apply(
            @Valid @RequestBody ResellerApplicationRequest request) {

        UUID userId = currentUserId();
        log.info("[RESELLER] Application submitted: userId={}", userId);
        ResellerApplicationResponse response = resellerService.applyForReseller(userId, request);
        log.info("[RESELLER] Application created: userId={} profileId={} status={}",
                userId, response.getProfileId(), response.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get reseller dashboard: revenue, profit, wallet balance, order count")
    @ApiResponse(responseCode = "200", description = "Dashboard returned")
    public ResponseEntity<ResellerDashboardResponse> getDashboard() {
        UUID userId = currentUserId();
        log.debug("[RESELLER] Dashboard requested: userId={}", userId);
        return ResponseEntity.ok(resellerService.getDashboard(userId));
    }

    // ── Store settings ────────────────────────────────────────────────────────

    @GetMapping("/store")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get current storefront branding settings")
    @ApiResponse(responseCode = "200", description = "Store settings returned")
    public ResponseEntity<StoreSettingsResponse> getStoreSettings() {
        UUID userId = currentUserId();
        log.debug("[RESELLER] Store settings requested: userId={}", userId);
        return ResponseEntity.ok(resellerService.getStoreSettings(userId));
    }

    @PutMapping("/store")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Update storefront branding (name, tagline, logo URL, theme colour)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Store settings updated"),
            @ApiResponse(responseCode = "400", description = "Invalid theme colour format"),
            @ApiResponse(responseCode = "403", description = "Reseller not approved")
    })
    public ResponseEntity<StoreSettingsResponse> updateStoreSettings(
            @Valid @RequestBody UpdateStoreSettingsRequest request) {

        UUID userId = currentUserId();
        log.info("[RESELLER] Store settings update: userId={}", userId);
        StoreSettingsResponse response = resellerService.updateStoreSettings(userId, request);
        log.info("[RESELLER] Store settings updated: userId={} profileId={}", userId, response.getProfileId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/store/share")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get shareable store link and referral link")
    @ApiResponse(responseCode = "200", description = "Share info returned")
    public ResponseEntity<StoreShareResponse> getShareInfo() {
        UUID userId = currentUserId();
        log.debug("[RESELLER] Share info requested: userId={}", userId);
        return ResponseEntity.ok(resellerService.getShareInfo(userId));
    }

    // ── Pricing ───────────────────────────────────────────────────────────────

    @GetMapping("/pricing")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "List all selling prices configured by this reseller")
    @ApiResponse(responseCode = "200", description = "Pricing table returned")
    public ResponseEntity<Page<ResellerPricingResponse>> getPricingTable(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        UUID userId = currentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by("network", "capacityGb"));
        log.debug("[RESELLER] Pricing table requested: userId={}", userId);
        return ResponseEntity.ok(resellerService.getPricingTable(userId, pageable));
    }

    @PutMapping("/pricing")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Create or update a reseller selling price (must be >= reseller cost price)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pricing row upserted"),
            @ApiResponse(responseCode = "400", description = "Selling price below cost price"),
            @ApiResponse(responseCode = "404", description = "No admin pricing found for that bundle")
    })
    public ResponseEntity<ResellerPricingResponse> upsertPricing(
            @Valid @RequestBody UpsertResellerPricingRequest request) {

        UUID userId = currentUserId();
        log.info("[RESELLER] Upsert pricing: userId={} network={} gb={} price={}",
                userId, request.getNetwork(), request.getCapacityGb(), request.getSellingPriceGhc());
        ResellerPricingResponse response = resellerService.upsertPricing(userId, request);
        log.info("[RESELLER] Pricing upserted: userId={} pricingId={}", userId, response.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/pricing/{pricingId}")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Delete a pricing row by ID (must belong to this reseller)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Pricing row deleted"),
            @ApiResponse(responseCode = "403", description = "Row does not belong to this reseller"),
            @ApiResponse(responseCode = "404", description = "Pricing row not found")
    })
    public ResponseEntity<Void> deletePricing(@PathVariable Long pricingId) {
        UUID userId = currentUserId();
        log.info("[RESELLER] Delete pricing: userId={} pricingId={}", userId, pricingId);
        resellerService.deletePricing(userId, pricingId);
        log.info("[RESELLER] Pricing deleted: userId={} pricingId={}", userId, pricingId);
        return ResponseEntity.noContent().build();
    }

    // ── Orders ────────────────────────────────────────────────────────────────

    @GetMapping("/orders")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Paginated list of all wholesale orders placed by this reseller")
    @ApiResponse(responseCode = "200", description = "Orders returned")
    public ResponseEntity<Page<ResellerOrderResponse>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = currentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        log.debug("[RESELLER] Orders requested: userId={} page={} size={}", userId, page, size);
        return ResponseEntity.ok(resellerService.getOrders(userId, pageable));
    }

    // ── Sub-customers ─────────────────────────────────────────────────────────

    @GetMapping("/sub-customers")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Paginated list of users who registered via this reseller's referral link")
    @ApiResponse(responseCode = "200", description = "Sub-customers returned")
    public ResponseEntity<Page<SubCustomerResponse>> getSubCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = currentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        log.debug("[RESELLER] Sub-customers requested: userId={}", userId);
        return ResponseEntity.ok(resellerService.getSubCustomers(userId, pageable));
    }

    // ── Payouts ───────────────────────────────────────────────────────────────

    @PostMapping("/payouts")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Request a payout to mobile money (must meet minimum payout threshold)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payout request submitted"),
            @ApiResponse(responseCode = "400", description = "Below minimum payout amount or insufficient balance"),
            @ApiResponse(responseCode = "403", description = "Reseller not approved")
    })
    public ResponseEntity<PayoutResponse> requestPayout(
            @Valid @RequestBody PayoutRequest request) {

        UUID userId = currentUserId();
        log.info("[RESELLER] Payout requested: userId={} amount={} network={}",
                userId, request.getAmount(), request.getNetwork());
        PayoutResponse response = resellerService.requestPayout(userId, request);
        log.info("[RESELLER] Payout request created: userId={} payoutId={} amount={}",
                userId, response.getId(), response.getAmount());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/payouts")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Paginated payout history for this reseller")
    @ApiResponse(responseCode = "200", description = "Payout history returned")
    public ResponseEntity<Page<PayoutResponse>> getPayoutHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = currentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        log.debug("[RESELLER] Payout history requested: userId={}", userId);
        return ResponseEntity.ok(resellerService.getPayoutHistory(userId, pageable));
    }
}