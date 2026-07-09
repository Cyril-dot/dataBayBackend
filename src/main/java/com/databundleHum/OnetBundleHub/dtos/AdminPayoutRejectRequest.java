package com.databundleHum.OnetBundleHub.dtos;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Payload for PATCH /api/admin/payouts/{id}/reject.
 * reason is required so the reseller knows why their payout was declined.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPayoutRejectRequest {

    @NotBlank(message = "Rejection reason is required")
    private String reason;
}