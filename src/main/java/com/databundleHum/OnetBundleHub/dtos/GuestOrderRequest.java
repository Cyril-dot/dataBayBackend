package com.databundleHum.OnetBundleHub.dtos;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class GuestOrderRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^(0[23456789]\\d{8})$",
        message = "Must be a valid Ghana phone number"
    )
    private String phoneNumber;

    @NotNull(message = "Network is required")
    private String network;

    @NotNull(message = "Capacity is required")
    @DecimalMin(value = "0.5", message = "Minimum bundle is 0.5 GB")
    @DecimalMax(value = "100.0", message = "Maximum bundle is 100 GB")
    private BigDecimal capacityGb;

    @NotBlank(message = "Paystack reference is required")
    private String paystackRef;
}