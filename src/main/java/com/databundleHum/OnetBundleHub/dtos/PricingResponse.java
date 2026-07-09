package com.databundleHum.OnetBundleHub.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Unified pricing row returned by GET /api/v1/pricing/effective.
 *
 * publicPriceGhc is named to match AdminPricingResponse's field so the
 * frontend's usePricingCatalog.priceFor() lookup (row.publicPriceGhc) works
 * unchanged whether the row came from admin's public pricing or a reseller's
 * custom override.
 */
@Data
@Builder
public class PricingResponse {
    private String     network;
    private BigDecimal capacityGb;
    private BigDecimal publicPriceGhc;
}