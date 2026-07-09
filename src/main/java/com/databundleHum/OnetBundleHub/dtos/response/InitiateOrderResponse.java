package com.databundleHum.OnetBundleHub.dtos.response;

import lombok.*;

import java.math.BigDecimal;

/**
 * Returned after POST /api/orders/initiate-guest.
 * Frontend uses paystackReference + amountPesewas to open the Paystack inline popup.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InitiateOrderResponse {
    private String     paystackReference;
    private BigDecimal amountGhc;
    /** Paystack requires amounts in the smallest unit (pesewas = kobo for GHS). */
    private long       amountPesewas;
    /** Paystack requires an email — platform guest email used for guest orders. */
    private String     email;
    private String     phoneNumber;
    private String     network;
    private BigDecimal capacityGb;
}