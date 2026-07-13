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
 *
 * isCustomPrice indicates whether publicPriceGhc came from a reseller's own
 * ResellerPricing override (true) or is the admin's fallback price (false).
 * This is irrelevant for a buyer (they just see one price either way) but
 * matters on a reseller's own pricing dashboard, where it lets them see at a
 * glance which bundles they haven't set a custom price for yet. Defaults to
 * false so existing call sites (admin public pricing, guest pricing) that
 * don't set it explicitly stay correct.
 */
@Data
@Builder
public class PricingResponse {
    private String     network;
    private BigDecimal capacityGb;
    private BigDecimal publicPriceGhc;
    @Builder.Default
    private boolean    isCustomPrice = false;
}