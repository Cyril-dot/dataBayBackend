package com.databundleHum.OnetBundleHub.dtos;

import com.databundleHum.OnetBundleHub.entity.PlatformSettings;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Buy data from wallet — used by both logged-in users and resellers.
 * POST /api/orders/wallet
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletOrderRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^0[2359]\\d{8}$", message = "Invalid Ghana phone number")
    private String phoneNumber;

    @NotNull(message = "Capacity is required")
    @DecimalMin(value = "0.5", message = "Minimum bundle size is 0.5 GB")
    private BigDecimal capacityGb;

    @NotNull(message = "Network is required")
    private PlatformSettings.Network network;
}