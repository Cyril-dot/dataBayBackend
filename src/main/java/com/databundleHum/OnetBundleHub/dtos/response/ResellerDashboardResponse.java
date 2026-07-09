package com.databundleHum.OnetBundleHub.dtos.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Returned by GET /api/v1/reseller/stats (and getDashboard).
 *
 * ── Field guide ───────────────────────────────────────────────────────────────
 *
 *  storeSlug           URL-safe slug for the reseller's public storefront,
 *                      e.g. "kwame-data". Set on approval, immutable after.
 *                      Used by the dashboard header to render the store link.
 *
 *  storeName           Effective display name (storeName if set, otherwise
 *                      the user's fullName). Used in the share flyout.
 *
 *  totalRevenueGhc     Sum of sellingPriceGhc across all storefront orders.
 *                      What customers paid into the reseller's store.
 *
 *  totalCostGhc        Sum of costPriceGhc (platform wholesale price) across
 *                      all storefront orders. What the platform charged to fill them.
 *
 *  totalProfitGhc      totalRevenueGhc − totalCostGhc (lifetime gross margin).
 *
 *  profitPaidGhc       Cumulative amount already paid out to the reseller via
 *                      approved payout requests. Persisted on ResellerProfile.
 *
 *  availableProfitGhc  totalProfitGhc − profitPaidGhc.
 *                      This is what the reseller can currently withdraw.
 *                      Computed by ResellerProfile.getAvailableProfitGhc().
 *
 *  walletBalanceGhc    The reseller's personal wallet balance (separate from
 *                      profit balance). Used for their own direct bundle purchases
 *                      and top-ups — not connected to storefront revenue.
 *
 *  totalOrders         Count of all orders placed via this reseller's storefront
 *                      (orderedByRole = RESELLER).
 */
@Data
@Builder
public class ResellerDashboardResponse {

    private UUID          profileId;
    private String        status;

    // ── Storefront identity ───────────────────────────────────────────────────

    /** URL-safe store slug, e.g. "kwame-data". Null until approved. */
    private String        storeSlug;

    /** Effective store display name (storeName or fullName fallback). */
    private String        storeName;

    // ── Profit & revenue ──────────────────────────────────────────────────────

    /** Total customer payments received through the storefront (GHS). */
    private BigDecimal    totalRevenueGhc;

    /** Total wholesale cost paid to the platform for storefront orders (GHS). */
    private BigDecimal    totalCostGhc;

    /** Lifetime gross profit = totalRevenueGhc − totalCostGhc (GHS). */
    private BigDecimal    totalProfitGhc;

    /** Amount already paid out to the reseller via approved payouts (GHS). */
    private BigDecimal    profitPaidGhc;

    /**
     * Amount available to withdraw = totalProfitGhc − profitPaidGhc (GHS).
     * This is the figure the "Request payout" button should validate against.
     */
    private BigDecimal    availableProfitGhc;

    // ── Personal wallet ───────────────────────────────────────────────────────

    /**
     * Reseller's personal wallet balance (GHS).
     * Separate from storefront profit — used for direct bundle top-ups.
     */
    private BigDecimal    walletBalanceGhc;

    // ── Meta ──────────────────────────────────────────────────────────────────

    private LocalDateTime approvedAt;
    private long          totalOrders;
}