package com.databundleHum.OnetBundleHub.dtos.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Data
@Builder
public class ResellerPricingResponse {
    private Long id;
    private String network;
    private BigDecimal capacityGb;
    private BigDecimal sellingPriceGhc;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}