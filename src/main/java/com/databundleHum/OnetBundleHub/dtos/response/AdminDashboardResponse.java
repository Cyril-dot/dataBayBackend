package com.databundleHum.OnetBundleHub.dtos.response;

import lombok.*;

import java.math.BigDecimal;

/**
 * Returned by GET /api/admin/dashboard.
 * All monetary fields are GHS.
 *
 * totalWalletLiabilitiesGhc and totalAffiliateEarningsLiabilityGhc are
 * DELIBERATELY separate figures — one is spendable/topped-up user money,
 * the other is unclaimed affiliate commission owed on payout. Do not sum
 * them together when displaying "money the platform owes users."
 *
 * totalRevenueGhc / totalCostGhc / totalProfitGhc (added 2026-07-23):
 * computed in AdminService.getDashboard() from Order.OrderStatus.VERIFIED
 * orders (sum of sellingPriceGhc, sum of costPriceGhc, and their
 * difference, respectively). Previously totalRevenueGhc was computed
 * against OrderStatus.DELIVERED, a status no order in the codebase ever
 * transitions to, so it was always stuck at zero — see AdminService for
 * the full fix note. totalProfitGhc is gross profit only; it does not
 * subtract affiliate commissions owed (tracked separately via
 * totalAffiliateEarningsLiabilityGhc) since commission owed isn't
 * necessarily commission already paid out.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDashboardResponse {
    private long       totalUsers;
    private long       totalResellers;
    private long       pendingResellerApplications;
    private long       totalOrders;
    private long       pendingPayouts;
    private BigDecimal totalRevenueGhc;
    private BigDecimal totalCostGhc;    // sum of costPriceGhc across VERIFIED orders
    private BigDecimal totalProfitGhc;  // totalRevenueGhc - totalCostGhc (gross profit)
    private BigDecimal totalWalletLiabilitiesGhc;   // sum of all user wallet balances
    private BigDecimal totalPendingPayoutsGhc;
    private BigDecimal totalAffiliateEarningsLiabilityGhc;  // sum of all users' affiliateEarningsGhc
}