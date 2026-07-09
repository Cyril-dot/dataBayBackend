// ─────────────────────────────────────────────────────────────────────────────
package com.databundleHum.OnetBundleHub.dtos;
 
import lombok.Builder;
import lombok.Data;
 
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
 
/**
 * Response for GET /api/v1/reseller/stats (and getDashboard)
 *
 * Changes from previous version:
 *  - Added profitPaidGhc and availableProfitGhc (architecture §3.7 profit split)
 *  - Added storeSlug and storeName for the dashboard header
 */
@Data
@Builder
public class ResellerDashboardResponse {
    private UUID          profileId;
    private String        status;
    private String        storeSlug;
    private String        storeName;
    // Revenue / profit
    private BigDecimal    totalRevenueGhc;
    private BigDecimal    totalCostGhc;
    private BigDecimal    totalProfitGhc;
    /** Amount already paid out to the reseller. */
    private BigDecimal    profitPaidGhc;
    /** totalProfitGhc - profitPaidGhc = what can be withdrawn. */
    private BigDecimal    availableProfitGhc;
    // Personal wallet (for own purchases — separate from profit)
    private BigDecimal    walletBalanceGhc;
    private LocalDateTime approvedAt;
    private long          totalOrders;
}