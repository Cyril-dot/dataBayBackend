package com.databundleHum.OnetBundleHub.dtos;

import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class ResellerApplyRequest {
@Size(max = 1000, message = "Application note must be at most 1000 characters")
private String applicationNote;
// Payment method for the GHS 20 registration fee
// WALLET = deduct from wallet, PAYSTACK = open Paystack popup
@NotNull
private FeePaymentMethod feePaymentMethod;
public enum FeePaymentMethod { WALLET, PAYSTACK }
}