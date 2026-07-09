package com.databundleHum.OnetBundleHub.dtos.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BigDreamsBundleResponse {

    private Long       id;
    private String     network;
    private String     size;           // e.g. "5GB"
    private int        sizeGb;         // e.g. 5
    private BigDecimal buyingPriceGhc; // what we pay (mapped from API "price")
    private String     validity;       // e.g. "30 Days"
    private boolean    hasCustomPrice;
}