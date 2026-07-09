package com.databundleHum.OnetBundleHub.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response for GET /api/v1/store/{slug}
 *
 * Public endpoint — no authentication required.
 * Contains the store's branding/customization data and the list of bundles
 * the reseller has priced at their custom selling prices.
 *
 * All new fields are nullable — resellers who haven't filled them in
 * simply won't have the corresponding UI elements rendered on the storefront.
 */
@Data
@Builder
public class StorefrontResponse {

    // ── Identity ──────────────────────────────────────────────────────────────

    private String           storeSlug;

    // ── Existing branding fields ──────────────────────────────────────────────

    private String           storeName;
    private String           storeTagline;
    private String           storeLogoUrl;
    /** Hex accent colour, e.g. "#1A73E8". Used for the storefront header/buttons. */
    private String           themeColour;

    // ── New customization fields ──────────────────────────────────────────────

    /**
     * WhatsApp number for the "Order via WhatsApp" button.
     * Frontend links to: https://wa.me/{whatsappNumber}?text=...
     * Null if the reseller has not configured a number.
     */
    private String           whatsappNumber;

    /**
     * Instagram handle (no '@' prefix).
     * Frontend links to: https://instagram.com/{instagramHandle}
     * Null if the reseller has not configured a handle.
     */
    private String           instagramHandle;

    /**
     * URL of the wide hero banner image rendered above the bundle list.
     * Null if not set — frontend should show a gradient placeholder.
     */
    private String           bannerImageUrl;

    /**
     * "About" paragraph rendered below the store header.
     * Null if not set — frontend should omit the section entirely.
     */
    private String           welcomeMessage;

    /**
     * CTA button shape applied to all action buttons on the storefront.
     * Values: "ROUNDED" | "SQUARE". Never null (defaults to "ROUNDED").
     */
    private String           buttonStyle;

    /**
     * Storefront colour scheme.
     * Values: "LIGHT" | "DARK". Never null (defaults to "LIGHT").
     */
    private String           storeTheme;

    // ── Bundle list ───────────────────────────────────────────────────────────

    private List<BundleItem> bundles;

    @Data
    @Builder
    public static class BundleItem {
        private String     network;
        private BigDecimal capacityGb;
        /** The reseller's custom selling price — what the customer pays. */
        private BigDecimal sellingPriceGhc;
    }
}