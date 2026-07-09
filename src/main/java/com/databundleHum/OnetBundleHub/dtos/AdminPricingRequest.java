package com.databundleHum.OnetBundleHub.dtos;

import com.databundleHum.OnetBundleHub.entity.PlatformSettings;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Super Admin sets or updates public + reseller price for a network/capacity pair.
 * POST /api/admin/settings/pricing
 *
 * Note: Bean Validation annotations are on the request fields for controller-level
 * validation only. The raw BigDecimal values are passed cleanly to the repository.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPricingRequest {

    @NotNull(message = "Network is required")
    private PlatformSettings.Network network;

    @NotNull(message = "Capacity is required")
    private BigDecimal capacityGb;

    @NotNull(message = "Public price is required")
    @DecimalMin(value = "0.01", message = "Public price must be positive")
    private BigDecimal publicPriceGhc;

    @NotNull(message = "Reseller price is required")
    @DecimalMin(value = "0.01", message = "Reseller price must be positive")
    private BigDecimal resellerPriceGhc;

    /** Whether this bundle size/network combo is available for purchase. */
    @Builder.Default
    private boolean active = true;
}