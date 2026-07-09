package com.databundleHum.OnetBundleHub.dtos.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ResellerApplicationResponse {
    private UUID profileId;
    private String status;
    private String message;
    private BigDecimal walletBalanceAfter;
}