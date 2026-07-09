package com.databundleHum.OnetBundleHub.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response for GET /api/v1/reseller/store and PUT /api/v1/reseller/store
 *
 * Returns all current store settings so the frontend can populate
 * the settings form (and live preview) without a separate fetch.
 * All new fields are nullable — a reseller may not have filled them in yet.
 */
@Data
@Builder
public class StoreSettingsResponse {

    // ── Identity ──────────────────────────────────────────────────────────────

    private UUID          profileId;
    /** Immutable URL slug, e.g. "kwame-data". Never null for an approved reseller. */
    private String        storeSlug;

    // ── Existing branding fields ──────────────────────────────────────────────

    private String        storeName;
    private String        storeTagline;
    private String        storeLogoUrl;
    /** Hex accent colour, e.g. "#1A73E8". */
    private String        themeColour;

    // ── New customization fields ──────────────────────────────────────────────

    /** WhatsApp number, e.g. "+233241234567". Null if not set. */
    private String        whatsappNumber;
    /** Instagram handle without '@', e.g. "kwamedata". Null if not set. */
    private String        instagramHandle;
    /** URL of the hero banner image. Null if not set. */
    private String        bannerImageUrl;
    /** "About" paragraph text. Null if not set. */
    private String        welcomeMessage;
    /** Button shape: "ROUNDED" or "SQUARE". Never null (defaults to ROUNDED). */
    private String        buttonStyle;
    /** Colour scheme: "LIGHT" or "DARK". Never null (defaults to LIGHT). */
    private String        storeTheme;

    // ── Audit ─────────────────────────────────────────────────────────────────

    private LocalDateTime updatedAt;
}