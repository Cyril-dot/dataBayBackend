package com.databundleHum.OnetBundleHub.dtos;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Initiate a Paystack wallet top-up.
 * POST /api/wallet/topup/initiate
 * Returns a Paystack reference — frontend opens Paystack popup with it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopUpInitiateRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum top-up amount is GHS 1.00")
    @DecimalMax(value = "10000.00", message = "Maximum single top-up is GHS 10,000")
    private BigDecimal amount;
}