package com.databundleHum.OnetBundleHub.dtos;

import com.databundleHum.OnetBundleHub.entity.PlatformSettings;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Step 1 of guest checkout.
 * Frontend sends this to POST /api/orders/initiate-guest.
 * Backend saves a PENDING order and returns a Paystack reference for the popup.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InitiateGuestOrderRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^0[2359]\\d{8}$", message = "Invalid Ghana phone number")
    private String phoneNumber;

    @NotNull(message = "Capacity is required")
    @DecimalMin(value = "0.5", message = "Minimum bundle size is 0.5 GB")
    private BigDecimal capacityGb;

    @NotNull(message = "Network is required")
    private PlatformSettings.Network network;
}