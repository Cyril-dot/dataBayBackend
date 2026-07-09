package com.databundleHum.OnetBundleHub.dtos.response;

import lombok.*;

import java.math.BigDecimal;

/**
 * Returned by GET /api/admin/dashboard.
 * All monetary fields are GHS.
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
}