package com.databundleHum.OnetBundleHub.dtos.response;

import com.databundleHum.OnetBundleHub.entity.ResellerProfile.ResellerStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Data
@Builder
public class ResellerProfileResponse {

    private Long id;
    private Long userId;
    private String fullName;
    private String email;
    private String phone;
    private ResellerStatus status;
    private BigDecimal totalRevenueGhc;
    private BigDecimal totalCostGhc;
    private BigDecimal totalProfitGhc;
    private String applicationNote;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
}