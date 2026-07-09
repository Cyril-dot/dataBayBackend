// ─────────────────────────────────────────────────────────────────────────────
package com.databundleHum.OnetBundleHub.dtos;
 
import lombok.Builder;
import lombok.Data;
 
import java.math.BigDecimal;
import java.time.LocalDateTime;
 
/**
 * Response item for GET /api/v1/affiliate/commissions
 *
 * One row per CommissionTransaction. Referred user is masked per privacy rules.
 */
@Data
@Builder
public class AffiliateCommissionResponse {
    private Long          id;
    /** Internal order ID the commission was earned on. */
    private Long          orderId;
    /** Network of the order, e.g. "MTN". */
    private String        orderNetwork;
    /** Bundle size of the order. */
    private java.math.BigDecimal orderCapacityGb;
    /** Masked referred user name: "Kwame A." */
    private String        referredUserMasked;
    /** Commission amount in GHS. */
    private BigDecimal    commissionGhc;
    /** True if this commission was reversed due to a refund. */
    private boolean       reversed;
    /** When the commission was reversed (null if not reversed). */
    private LocalDateTime reversedAt;
    /** When the commission was originally credited. */
    private LocalDateTime createdAt;
}