package com.databundleHum.OnetBundleHub.dtos.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Returned for every order — guest, user wallet, reseller wallet, and storefront.
 * All enum fields are serialised to their .name() String so the frontend
 * receives plain text (e.g. "MTN", "PENDING", "WALLET").
 *
 * ── Field guide ───────────────────────────────────────────────────────────────
 *
 *  storefrontOrder     true when the order was placed through a reseller's public
 *                      storefront (/store/{slug}). The frontend uses this to show
 *                      a "Purchased via {resellerStoreName}" badge on the order card.
 *
 *  resellerStoreName   Display name of the storefront (storeName or fullName fallback).
 *                      Null when storefrontOrder = false.
 *
 *  profitGhc           sellingPriceGhc − costPriceGhc.
 *                      Populated only for RESELLER role orders (direct or storefront)
 *                      where the two prices differ. Zero for regular user orders.
 *                      Used on the reseller's order history table.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private Long       id;
    private String     phoneNumber;

    /** PlatformSettings.Network.name() — e.g. "MTN", "TELECEL", "AT_PREMIUM" */
    private String     network;

    private BigDecimal capacityGb;
    private BigDecimal costPriceGhc;
    private BigDecimal sellingPriceGhc;

    /**
     * sellingPriceGhc − costPriceGhc.
     * Non-zero only for reseller orders where the reseller charges more than cost.
     * Always zero for regular user and guest orders.
     */
    private BigDecimal profitGhc;

    /** Order.PaymentMethod.name() — "WALLET" or "PAYSTACK" */
    private String     paymentMethod;

    /** Paystack transaction reference. Null for wallet-funded orders. */
    private String     paystackRef;

    /** Order.OrderStatus.name() — "PENDING", "VERIFIED", "DELIVERED", "FAILED" */
    private String     status;

    /** True when placed without a user account (Paystack guest checkout). */
    private boolean    guest;

    /**
     * True when the order originated from a reseller's public storefront.
     * When true, resellerStoreName is populated.
     */
    private boolean    storefrontOrder;

    /**
     * Effective display name of the reseller's store (storeName or fullName fallback).
     * Null when storefrontOrder = false.
     */
    private String     resellerStoreName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}