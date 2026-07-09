package com.databundleHum.OnetBundleHub.dtos;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for PUT /api/v1/reseller/store
 *
 * All fields are optional — only non-null values are applied to the profile.
 * The slug is immutable and cannot be changed through this endpoint.
 *
 * Validation annotations are enforced by Spring's @Valid on the controller method.
 */
@Data
public class UpdateStoreSettingsRequest {

    // ── Existing fields ───────────────────────────────────────────────────────

    /** Display name shown on the storefront. */
    @Size(max = 200, message = "Store name must be 200 characters or fewer")
    private String storeName;

    /** One-line tagline, e.g. "Fast data bundles — all networks". */
    @Size(max = 300, message = "Tagline must be 300 characters or fewer")
    private String storeTagline;

    /** URL to the reseller's logo (upload first, then pass the URL). */
    private String storeLogoUrl;

    /**
     * Hex accent colour, e.g. "#1A73E8".
     * Pattern validated here as a belt-and-suspenders check;
     * ResellerServiceImpl also validates before persisting.
     */
    @Pattern(
        regexp = "^#[0-9A-Fa-f]{6}$",
        message = "themeColour must be a valid 6-digit hex colour, e.g. #1A73E8"
    )
    private String themeColour;

    // ── New fields ────────────────────────────────────────────────────────────

    /**
     * WhatsApp contact number shown on the storefront.
     * Must contain only digits and an optional leading '+'. Max 20 chars.
     * Example: "+233241234567" or "0241234567"
     */
    @Pattern(
        regexp = "^\\+?[0-9]{1,19}$",
        message = "whatsappNumber must contain only digits with an optional leading '+', max 20 characters"
    )
    @Size(max = 20, message = "whatsappNumber must be 20 characters or fewer")
    private String whatsappNumber;

    /**
     * Instagram handle, without the '@' prefix. Max 60 chars.
     * The service will strip a leading '@' if the reseller accidentally includes one.
     * Example: "kwamedata" (not "@kwamedata")
     */
    @Size(max = 60, message = "instagramHandle must be 60 characters or fewer")
    @Pattern(
        regexp = "^[^@].*|^$",
        message = "instagramHandle must not include a leading '@'"
    )
    private String instagramHandle;

    /** URL to the wide hero banner image displayed above the bundle list. */
    private String bannerImageUrl;

    /** "About" paragraph shown on the store page. No length cap enforced here — use TEXT in DB. */
    private String welcomeMessage;

    /**
     * Controls CTA button shape on the storefront.
     * Accepted values: "ROUNDED", "SQUARE". Case-sensitive.
     */
    @Pattern(
        regexp = "^(ROUNDED|SQUARE)$",
        message = "buttonStyle must be either 'ROUNDED' or 'SQUARE'"
    )
    private String buttonStyle;

    /**
     * Controls the storefront colour scheme.
     * Accepted values: "LIGHT", "DARK". Case-sensitive.
     */
    @Pattern(
        regexp = "^(LIGHT|DARK)$",
        message = "storeTheme must be either 'LIGHT' or 'DARK'"
    )
    private String storeTheme;
}