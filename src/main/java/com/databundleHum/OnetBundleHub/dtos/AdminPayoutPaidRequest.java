package com.databundleHum.OnetBundleHub.dtos;

import lombok.*;

/**
 * Payload for PATCH /api/admin/payouts/{id}/pay.
 * adminNote is optional — e.g. the MoMo transaction reference.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPayoutPaidRequest {

    private String adminNote;
}