package com.databundleHum.OnetBundleHub.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reseller_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResellerProfile {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ResellerStatus status = ResellerStatus.PENDING;

    // ── Storefront identity ───────────────────────────────────────────────────

    /**
     * URL-safe slug derived from the reseller's full name at approval time.
     * Format: "kwame-data", "kwame-data-2" (suffixed if slug already taken).
     * Immutable after set — changing it would break live store links.
     */
    @Column(name = "store_slug", length = 100, unique = true)
    private String storeSlug;

    /**
     * Display name shown on the public storefront.
     * Defaults to the user's full name if not explicitly set.
     */
    @Column(name = "store_name", length = 200)
    private String storeName;

    /**
     * One-line tagline shown below the store name on the public storefront.
     */
    @Column(name = "store_tagline", length = 300)
    private String storeTagline;

    /**
     * URL to the reseller's uploaded logo (stored in file storage).
     */
    @Column(name = "store_logo_url", columnDefinition = "TEXT")
    private String storeLogoUrl;

    /**
     * Hex accent colour for the storefront header, e.g. "#1A73E8".
     */
    @Column(name = "theme_colour", length = 7)
    private String themeColour;

    // ── New storefront customization fields ───────────────────────────────────

    /**
     * WhatsApp number (digits and leading + only, max 20 chars).
     * Renders an "Order via WhatsApp" button on the storefront.
     * Links to: https://wa.me/{whatsappNumber}?text=Hi%2C+I'd+like+to+order+from+{storeName}
     */
    @Column(name = "whatsapp_number", length = 20)
    private String whatsappNumber;

    /**
     * Instagram handle, without the @ prefix (max 60 chars).
     * Renders an Instagram icon link on the storefront.
     * Links to: https://instagram.com/{instagramHandle}
     */
    @Column(name = "instagram_handle", length = 60)
    private String instagramHandle;

    /**
     * URL to a wide hero banner image shown above the bundle list.
     */
    @Column(name = "banner_image_url", columnDefinition = "TEXT")
    private String bannerImageUrl;

    /**
     * Rich "about" paragraph shown on the store page below the header.
     */
    @Column(name = "welcome_message", columnDefinition = "TEXT")
    private String welcomeMessage;

    /**
     * Controls CTA button shape on the storefront: ROUNDED or SQUARE.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "button_style", length = 10)
    @Builder.Default
    private ButtonStyle buttonStyle = ButtonStyle.ROUNDED;

    /**
     * Controls the storefront colour scheme: LIGHT or DARK.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "store_theme", length = 10)
    @Builder.Default
    private StoreTheme storeTheme = StoreTheme.LIGHT;

    // ── Financial aggregates ──────────────────────────────────────────────────

    @Column(name = "total_revenue_ghc", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalRevenueGhc = BigDecimal.ZERO;

    @Column(name = "total_cost_ghc", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalCostGhc = BigDecimal.ZERO;

    @Column(name = "total_profit_ghc", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalProfitGhc = BigDecimal.ZERO;

    @Column(name = "profit_paid_ghc", nullable = false, precision = 12, scale = 2,
            columnDefinition = "numeric(12,2) default 0.00")
    @Builder.Default
    private BigDecimal profitPaidGhc = BigDecimal.ZERO;

    // ── Approval metadata ─────────────────────────────────────────────────────

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "application_note", columnDefinition = "TEXT")
    private String applicationNote;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // ── Audit timestamps ──────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Computed helpers ──────────────────────────────────────────────────────

    public BigDecimal getAvailableProfitGhc() {
        return totalProfitGhc.subtract(profitPaidGhc);
    }

    public String getEffectiveStoreName() {
        return (storeName != null && !storeName.isBlank()) ? storeName : user.getFullName();
    }

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum ResellerStatus {
        PENDING, APPROVED, REJECTED, SUSPENDED
    }

    public enum ButtonStyle {
        ROUNDED, SQUARE
    }

    public enum StoreTheme {
        LIGHT, DARK
    }
}