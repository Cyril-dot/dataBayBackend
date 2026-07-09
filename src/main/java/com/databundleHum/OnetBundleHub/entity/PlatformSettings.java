package com.databundleHum.OnetBundleHub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Stores per-network, per-capacity pricing rows set by the Super Admin.
 * Also acts as the single source of truth for reseller cost prices.
 *
 * One row = one (network, capacity_gb) combination.
 * setting_key is auto-derived as "{NETWORK}_{GB}GB" — e.g. "MTN_1GB"
 */
@Entity
@Table(
        name = "platform_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_platform_settings_network_capacity",
                columnNames = {"network", "capacity_gb"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Derived unique key — auto-generated from network + capacityGb.
     * Format: "MTN_1GB", "TELECEL_5GB", etc.
     * Never set this manually — @PrePersist and @PreUpdate handle it.
     */
    @Column(name = "setting_key", nullable = false, unique = true, length = 50)
    private String settingKey;

    /**
     * Network this pricing row applies to.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Network network;

    /**
     * Bundle size in GB this row covers.
     */
    @Column(name = "capacity_gb", nullable = false, precision = 6, scale = 2)
    private BigDecimal capacityGb;

    /**
     * Price charged to regular users / guests (GHS).
     */
    @Column(name = "public_price_ghc", nullable = false, precision = 10, scale = 2)
    private BigDecimal publicPriceGhc;

    /**
     * Wholesale price charged to approved resellers (GHS).
     * Must always be < publicPriceGhc.
     */
    @Column(name = "reseller_price_ghc", nullable = false, precision = 10, scale = 2)
    private BigDecimal resellerPriceGhc;

    /**
     * Whether this bundle is currently purchasable.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    // ── Lifecycle hooks ────────────────────────────────────────────────────────

    @PrePersist
    public void onCreate() {
        deriveSettingKey();
        if (this.createdAt == null) this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        deriveSettingKey();
        this.updatedAt = Instant.now();
    }

    private void deriveSettingKey() {
        if (this.network != null && this.capacityGb != null) {
            // Strip trailing zeros: 1.00 → "1", 1.50 → "1.5"
            String gbStr = this.capacityGb.stripTrailingZeros().toPlainString();
            this.settingKey = this.network.name() + "_" + gbStr + "GB";
        }
    }

    // ── Enums ──────────────────────────────────────────────────────────────────

    public enum Network {
        MTN, TELECEL, AIRTELTIGO
    }
}