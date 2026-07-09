package com.databundleHum.OnetBundleHub.dtos;
 
import lombok.Builder;
import lombok.Data;
 
import java.math.BigDecimal;
import java.util.UUID;
 
/**
 * Response for GET /api/v1/affiliate/dashboard
 */
@Data
@Builder
public class AffiliateDashboardResponse {
    private UUID       userId;
    /** The affiliate's unique referral code. */
    private String     affiliateCode;
    /** Full referral URL for copy/share. */
    private String     referralUrl;
    /**
     * Total users who registered via this affiliate's link.
     * Includes users who signed up but haven't ordered yet.
     */
    private long       totalSignUps;
    /**
     * Distinct users who signed up via this link AND have placed
     * at least one qualifying order (i.e. commission was earned).
     */
    private long       referredUsersWithOrders;
    /** Total commission earned all time (non-reversed GHS). */
    private BigDecimal totalCommissionEarnedGhc;
    /** Commission earned in the current calendar month (non-reversed GHS). */
    private BigDecimal thisMonthCommissionGhc;
    /**
     * The affiliate's current wallet balance.
     * Commissions land in the same wallet used for purchases.
     */
    private BigDecimal walletBalanceGhc;
}
 