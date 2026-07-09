package com.databundleHum.OnetBundleHub.dtos;
 
import lombok.Builder;
import lombok.Data;
 
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
 
/**
 * Response for admin reseller list / approve / reject endpoints.
 *
 * New fields: storeSlug, storeName, availableProfitGhc.
 */
@Data
@Builder
public class AdminResellerResponse {
    private UUID          profileId;
    private UUID          userId;
    private String        fullName;
    private String        email;
    private String        phone;
    private String        status;
    private String        storeSlug;
    private String        storeName;
    private String        applicationNote;
    private String        rejectionReason;
    private BigDecimal    totalRevenueGhc;
    private BigDecimal    totalCostGhc;
    private BigDecimal    totalProfitGhc;
    private BigDecimal    availableProfitGhc;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
}
 