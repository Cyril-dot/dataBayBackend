package com.databundleHum.OnetBundleHub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "reseller_pricing",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_reseller_pricing_reseller_network_capacity",
        columnNames = {"reseller_id", "network", "capacity_gb"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResellerPricing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reseller_id", nullable = false)
    private User reseller;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlatformSettings.Network network;

    @Column(name = "capacity_gb", nullable = false, precision = 6, scale = 2)
    private BigDecimal capacityGb;

    /** Price the reseller charges their own customers (must be >= reseller cost price). */
    @Column(name = "selling_price_ghc", nullable = false, precision = 10, scale = 2)
    private BigDecimal sellingPriceGhc;

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
}