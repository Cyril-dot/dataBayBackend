package com.databundleHum.OnetBundleHub.dtos.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Returned by GET /api/wallet/transactions and GET /api/admin/transactions.
 *
 * type is a String (e.g. "TOPUP", "PURCHASE") so the frontend receives
 * plain text without needing to know the enum.
 * Use WalletTransaction.TransactionType.name() in every mapper.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransactionResponse {
    private Long       id;
    private String     type;        // TransactionType.name() — e.g. "TOPUP", "PURCHASE"
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String     reference;
    private String     description;
    private Instant    createdAt;
}