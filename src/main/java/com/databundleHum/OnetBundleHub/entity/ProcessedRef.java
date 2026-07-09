package com.databundleHum.OnetBundleHub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Tracks every Paystack reference that has already been processed.
 * Prevents duplicate crediting if Paystack sends a webhook more than once.
 */
@Entity
@Table(name = "processed_refs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique Paystack transaction reference (e.g. "ps_ref_abc123").
     */
    @Column(name = "reference", nullable = false, unique = true, length = 100)
    private String reference;

    /**
     * High-level event type that was processed (WALLET_TOPUP, GUEST_ORDER, RESELLER_FEE).
     */
    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant processedAt = Instant.now();
}