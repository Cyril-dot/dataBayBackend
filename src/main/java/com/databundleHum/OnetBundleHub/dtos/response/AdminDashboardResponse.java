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
    private BigDecimal totalWalletLiabilitiesGhc;   // sum of all user wallet balances
    private BigDecimal totalPendingPayoutsGhc;
    private BigDecimal totalAffiliateEarningsLiabilityGhc;  // sum of all users' affiliateEarningsGhc
}