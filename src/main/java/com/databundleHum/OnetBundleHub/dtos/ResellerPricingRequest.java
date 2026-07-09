package com.databundleHum.OnetBundleHub.dtos;

import com.databundleHum.OnetBundleHub.entity.PlatformSettings;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

/**
 * Reseller sets or updates their selling price for a specific bundle.
 */
@Data
public class ResellerPricingRequest {

    @NotNull(message = "Network is required")
    private PlatformSettings.Network network;

    @NotNull(message = "Capacity is required")
    @DecimalMin(value = "0.5", message = "Minimum bundle is 0.5 GB")
    @DecimalMax(value = "100.0", message = "Maximum bundle is 100 GB")
    private BigDecimal capacityGb;

    // Service enforces: sellingPriceGhc >= admin reseller cost price
    @NotNull(message = "Selling price is required")
    @DecimalMin(value = "0.01", message = "Price must be positive")
    private BigDecimal sellingPriceGhc;
}