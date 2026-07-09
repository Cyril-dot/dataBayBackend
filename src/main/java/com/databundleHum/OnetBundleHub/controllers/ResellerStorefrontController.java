package com.databundleHum.OnetBundleHub.controllers;

import com.databundleHum.OnetBundleHub.dtos.InitiateGuestStorefrontOrderRequest;
import com.databundleHum.OnetBundleHub.dtos.StorefrontResponse;
import com.databundleHum.OnetBundleHub.dtos.WalletOrderRequest;
import com.databundleHum.OnetBundleHub.dtos.response.OrderResponse;
import com.databundleHum.OnetBundleHub.security.UserPrincipal;
import com.databundleHum.OnetBundleHub.services.ResellerStorefrontService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Public-facing controller for reseller storefronts.
 *
 * <p>Browse and guest checkout endpoints are fully public — no authentication required.
 * Wallet checkout requires a logged-in customer (any authenticated role).
 *
 * <p>Base path: {@code /api/v1/storefront/{slug}}
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/storefront/{slug}")
@RequiredArgsConstructor
@Tag(name = "Storefront", description = "Public reseller storefront — browse bundles and place orders")
public class ResellerStorefrontController {

    private final ResellerStorefrontService resellerStorefrontService;

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return principal.userId();
    }

    // ── Browse ────────────────────────────────────────────────────────────────

    /**
     * Public endpoint — no auth required.
     * Returns store branding and the list of bundles the reseller has priced.
     */
    @GetMapping
    @Operation(summary = "Fetch a reseller's public storefront (branding + bundle list)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Storefront returned"),
            @ApiResponse(responseCode = "404", description = "Store not found or not approved")
    })
    public ResponseEntity<StorefrontResponse> getStorefront(@PathVariable String slug) {
        log.debug("[STOREFRONT] Browse request: slug={}", slug);
        StorefrontResponse response = resellerStorefrontService.getStorefront(slug);
        log.debug("[STOREFRONT] Storefront returned: slug={} bundleCount={}",
                slug, response.getBundles().size());
        return ResponseEntity.ok(response);
    }

    // ── Guest order (Paystack) ────────────────────────────────────────────────

    /**
     * Public endpoint — no auth required.
     * Initiates a Paystack payment for a guest customer on the reseller's storefront.
     * Returns the Paystack reference for the frontend to open the payment popup.
     */
    @PostMapping("/orders/guest")
    @Operation(summary = "Initiate a guest Paystack order through a reseller's storefront")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order initiated, Paystack reference returned"),
            @ApiResponse(responseCode = "404", description = "Store not found, not approved, or bundle unavailable")
    })
    public ResponseEntity<OrderResponse> initiateGuestOrder(
            @PathVariable String slug,
            @Valid @RequestBody InitiateGuestStorefrontOrderRequest request) {

        log.info("[STOREFRONT] Guest order initiation: slug={} phone={} network={} gb={}",
                slug, request.getPhoneNumber(), request.getNetwork(), request.getCapacityGb());
        OrderResponse response = resellerStorefrontService.initiateGuestStorefrontOrder(slug, request);
        log.info("[STOREFRONT] Guest order initiated: slug={} orderId={} ref={}",
                slug, response.getId(), response.getPaystackRef());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Wallet order (authenticated customer) ────────────────────────────────

    /**
     * Authenticated endpoint — any logged-in user may place an order.
     * Deducts the reseller's selling price from the customer's wallet.
     */
    @PostMapping("/orders/wallet")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Place a wallet-funded order through a reseller's storefront (requires login)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order placed and bundle dispatched"),
            @ApiResponse(responseCode = "400", description = "Insufficient wallet balance"),
            @ApiResponse(responseCode = "404", description = "Store not found, not approved, or bundle unavailable"),
            @ApiResponse(responseCode = "409", description = "Duplicate order detected within the last 30 seconds")
    })
    public ResponseEntity<OrderResponse> placeWalletOrder(
            @PathVariable String slug,
            @Valid @RequestBody WalletOrderRequest request) {

        UUID customerId = currentUserId();
        log.info("[STOREFRONT] Wallet order request: slug={} customerId={} phone={} network={} gb={}",
                slug, customerId, request.getPhoneNumber(), request.getNetwork(), request.getCapacityGb());
        OrderResponse response = resellerStorefrontService.placeWalletStorefrontOrder(slug, customerId, request);
        log.info("[STOREFRONT] Wallet order placed: slug={} customerId={} orderId={}",
                slug, customerId, response.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}