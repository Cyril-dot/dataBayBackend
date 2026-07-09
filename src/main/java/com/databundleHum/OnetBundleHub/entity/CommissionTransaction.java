package com.databundleHum.OnetBundleHub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Audit record for every affiliate commission credit or reversal.
 *
 * One row is created per qualifying order delivery by a referred user.
 * When the underlying order is refunded, the row is flagged reversed=true
 * and a debit is issued from the affiliate's wallet.
 *
 * Schema (Flyway migration V_next__add_affiliate_system.sql):
 *
 *   CREATE TABLE commission_transactions (
 *     id                 BIGSERIAL     PRIMARY KEY,
 *     affiliate_user_id  UUID          NOT NULL REFERENCES users(id),
 *     referred_user_id   UUID          NOT NULL REFERENCES users(id),
 *     order_id           BIGINT        NOT NULL REFERENCES orders(id),
 *     commission_ghc     NUMERIC(12,2) NOT NULL,
 *     reversed           BOOLEAN       NOT NULL DEFAULT FALSE,
 *     reversed_at        TIMESTAMP,
 *     created_at         TIMESTAMP     NOT NULL DEFAULT NOW()
 *   );
 *
 *   CREATE INDEX idx_commission_affiliate ON commission_transactions(affiliate_user_id);
 *   CREATE INDEX idx_commission_order     ON commission_transactions(order_id);
 */
@Entity
@Table(name = "commission_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommissionTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The affiliate who earns/loses this commission.
     * FK → users.id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affiliate_user_id", nullable = false)
    private User affiliateUser;

    /**
     * The referred user whose order generated this commission.
     * FK → users.id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_user_id", nullable = false)
    private User referredUser;

    /**
     * The order that triggered this commission.
     * FK → orders.id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * Commission amount in GHS (2% of order.sellingPriceGhc at the time
     * of delivery). Immutable after creation.
     */
    @Column(name = "commission_ghc", nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionGhc;

    /**
     * True if this commission has been reversed due to order refund.
     * Reversed commissions are deducted from the affiliate's wallet via
     * a AFFILIATE_COMMISSION_REVERSAL wallet transaction.
     *
     * If the affiliate's wallet balance is too low to cover the reversal,
     * the debit still proceeds (balance goes negative is prevented by
     * WalletService — see PENDING_REVERSAL note in architecture §9).
     */
    @Column(name = "reversed", nullable = false)
    @Builder.Default
    private boolean reversed = false;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}