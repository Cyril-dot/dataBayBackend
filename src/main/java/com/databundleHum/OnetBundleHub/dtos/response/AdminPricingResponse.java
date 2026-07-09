package com.databundleHum.OnetBundleHub.dtos.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Returned by GET /api/admin/settings/pricing and upsert/toggle actions. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPricingResponse {
    private Long       id;
    private String     network;          // PlatformSettings.Network.name()
    private BigDecimal capacityGb;
    private BigDecimal publicPriceGhc;
    private BigDecimal resellerPriceGhc;
    private boolean    active;
    private Instant    updatedAt;
}