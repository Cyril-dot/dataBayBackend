package com.databundleHum.OnetBundleHub.dtos;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Manually verify a Paystack wallet top-up.
 * POST /api/wallet/topup/verify
 * Used as a fallback when the Paystack webhook was missed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopUpVerifyRequest {

    @NotBlank(message = "Paystack reference is required")
    private String paystackRef;
}