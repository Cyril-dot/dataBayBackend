package com.databundleHum.OnetBundleHub.dtos;

import com.databundleHum.OnetBundleHub.entity.PlatformSettings;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpsertResellerPricingRequest {

    @NotNull(message = "Network is required")
    private PlatformSettings.Network network;

    @NotNull(message = "Capacity (GB) is required")
    @DecimalMin(value = "0.1", message = "Capacity must be at least 0.1 GB")
    private BigDecimal capacityGb;

    @NotNull(message = "Selling price is required")
    @DecimalMin(value = "0.01", message = "Selling price must be positive")
    private BigDecimal sellingPriceGhc;
}