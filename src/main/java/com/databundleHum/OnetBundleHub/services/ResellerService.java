package com.databundleHum.OnetBundleHub.services;

import com.databundleHum.OnetBundleHub.dtos.*;
import com.databundleHum.OnetBundleHub.dtos.response.*;
import com.databundleHum.OnetBundleHub.dtos.response.ResellerDashboardResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ResellerService {

    // ── Application & profile ─────────────────────────────────────────────────

    /**
     * Apply to become a reseller. GHS 20 is debited from the user's wallet.
     * Creates a ResellerProfile with status=PENDING.
     */
    ResellerApplicationResponse applyForReseller(UUID userId, ResellerApplicationRequest request);

    /**
     * Fetch the full dashboard summary: lifetime revenue, cost, profit,
     * wallet balance, available profit balance, and order count.
     */
    ResellerDashboardResponse getDashboard(UUID userId);

    // ── Store settings ────────────────────────────────────────────────────────

    /**
     * Get the reseller's current store settings (name, tagline, logo URL,
     * theme colour, slug, and share URLs).
     */
    StoreSettingsResponse getStoreSettings(UUID userId);

    /**
     * Update the reseller's storefront branding fields.
     * The slug is immutable and cannot be changed through this method.
     */
    StoreSettingsResponse updateStoreSettings(UUID userId, UpdateStoreSettingsRequest request);

    /**
     * Return the store link, referral link, and store name for the share flyout.
     * QR code generation is always done client-side.
     */
    StoreShareResponse getShareInfo(UUID userId);

    // ── Pricing ───────────────────────────────────────────────────────────────

    /**
     * List all selling prices the reseller has configured.
     */
    Page<ResellerPricingResponse> getPricingTable(UUID userId, Pageable pageable);

    /**
     * Create or update a pricing row for a specific network + capacity combination.
     * Selling price must be >= the platform reseller cost price.
     */
    ResellerPricingResponse upsertPricing(UUID userId, UpsertResellerPricingRequest request);

    /**
     * Delete a single pricing row by its ID. The row must belong to this reseller.
     * Deleting a row hides that bundle from the storefront immediately.
     */
    void deletePricing(UUID userId, Long pricingId);

    // ── Orders ────────────────────────────────────────────────────────────────

    /**
     * Paginated list of all storefront orders placed through this reseller's store.
     */
    Page<ResellerOrderResponse> getOrders(UUID userId, Pageable pageable);

    // ── Sub-customers ─────────────────────────────────────────────────────────

    /**
     * Paginated list of users who registered via this reseller's referral link.
     * Returns masked info (first name + last initial) per privacy rules.
     */
    Page<SubCustomerResponse> getSubCustomers(UUID userId, Pageable pageable);

    // ── Payouts ───────────────────────────────────────────────────────────────

    /**
     * Request a payout of accumulated storefront profit.
     * Validates: amount >= min threshold, amount <= available profit balance.
     */
    PayoutResponse requestPayout(UUID userId, PayoutRequest request);

    /**
     * Paginated list of the reseller's own payout history.
     */
    Page<PayoutResponse> getPayoutHistory(UUID userId, Pageable pageable);
}