package com.databundleHum.OnetBundleHub.dtos.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** Returned by GET /api/admin/resellers and approve/reject actions. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminResellerResponse {
    private UUID profileId;
    private UUID       userId;
    private String     fullName;
    private String     email;
    private String     phone;
    private String     status;           // ResellerProfile.ResellerStatus.name()
    private String     applicationNote;
    private String     rejectionReason;
    private BigDecimal totalRevenueGhc;
    private BigDecimal totalCostGhc;
    private BigDecimal totalProfitGhc;
    private LocalDateTime approvedAt;       // null until APPROVED
    private LocalDateTime    createdAt;
}