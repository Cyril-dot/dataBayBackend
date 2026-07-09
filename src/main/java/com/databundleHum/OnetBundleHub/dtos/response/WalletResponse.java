package com.databundleHum.OnetBundleHub.dtos.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Returned by GET /api/wallet/balance and wallet top-up verify.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletResponse {
    private UUID userId;
    private BigDecimal balance;
}