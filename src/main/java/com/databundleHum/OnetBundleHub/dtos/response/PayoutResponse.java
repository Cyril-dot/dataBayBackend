package com.databundleHum.OnetBundleHub.dtos.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Returned for every payout — reseller's own history and admin payout list.
 * All timestamps are Instant (UTC) — Jackson serialises to ISO-8601 string.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayoutResponse {
    private Long       id;
    private BigDecimal amount;
    private String     mobileMoneyNumber;
    private String     network;      // PlatformSettings.Network.name()
    private String     status;       // Payout.PayoutStatus.name()
    private String     adminNote;
    private LocalDateTime paidAt;       // null until PAID
    private LocalDateTime    createdAt;
}