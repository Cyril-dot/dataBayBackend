package com.databundleHum.OnetBundleHub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A cash-out request against one of two SEPARATE earnings pots:
 *   - RESELLER_PROFIT      → drawn from ResellerProfile.availableProfitGhc
 *   - AFFILIATE_COMMISSION → drawn from User.affiliateEarningsGhc
 *
 * Neither source is ever the requesting user's walletBalance — that money
 * is topped-up/spendable funds and is never eligible for payout.
 *
 * Flyway migration: V_next__add_payout_source.sql
 *
 *   ALTER TABLE payouts
 *     ADD COLUMN IF NOT EXISTS source VARCHAR(30) NOT NULL DEFAULT 'RESELLER_PROFIT';
 */
@Entity
@Table(name = "payouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user receiving this payout. Historically reseller-only (hence the
     * column name), now also used for affiliate payouts — see {@link #source}
     * to tell which earnings pot this draws from.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reseller_id", nullable = false)
    private User reseller;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "mobile_money_number", nullable = false, length = 20)
    private String mobileMoneyNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlatformSettings.Network network;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PayoutStatus status = PayoutStatus.PENDING;

    /**
     * Which earnings pot this payout draws from. Defaults to RESELLER_PROFIT
     * for backward compatibility with rows created before affiliate payouts
     * existed.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PayoutSource source = PayoutSource.RESELLER_PROFIT;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by")
    private User paidBy;

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

    public enum PayoutStatus {
        PENDING, PROCESSING, PAID, REJECTED
    }

    public enum PayoutSource {
        RESELLER_PROFIT, AFFILIATE_COMMISSION
    }
}