package com.databundleHum.OnetBundleHub.dtos;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Returned by GET /api/affiliate/dashboard.
 * All monetary fields are GHS.
 *
 * NOTE: availableEarningsGhc reflects User.affiliateEarningsGhc — the
 * affiliate's payout-eligible commission balance. This is DELIBERATELY not
 * the same as the user's wallet balance (spendable/topped-up money); the
 * two are separate pots and this DTO only ever exposes the earnings side.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AffiliateDashboardResponse {
    private UUID       userId;
    private String     affiliateCode;
    private String     referralUrl;
    private long       totalSignUps;
    private long       referredUsersWithOrders;
    private BigDecimal totalCommissionEarnedGhc;
    private BigDecimal thisMonthCommissionGhc;
    private BigDecimal availableEarningsGhc;
}