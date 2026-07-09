package com.databundleHum.OnetBundleHub.dtos.response;

import lombok.*;

import java.math.BigDecimal;

/**
 * Returned after POST /api/wallet/topup/initiate.
 * Frontend uses authorizationUrl to redirect to Paystack checkout,
 * and paystackReference to verify the payment afterward.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopUpInitiateResponse {
    private String     paystackReference;
    private BigDecimal amountGhc;
    private long       amountPesewas;
    private String     email;
    private String     authorizationUrl;   // Paystack checkout redirect URL
}