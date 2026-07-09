package com.databundleHum.OnetBundleHub.services;

import com.databundleHum.OnetBundleHub.repos.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Processes inbound Paystack webhook events.
 *
 * Called from WebhookController AFTER HMAC-SHA512 signature has been validated.
 *
 * Supported events:
 *   charge.success  → WALLET_TOPUP | GUEST_ORDER | RESELLER_FEE
 *   charge.failed   → mark order FAILED
 *   transfer.success → (Phase 4) automated payout confirmation
 *
 * The "metadata.type" field in the Paystack transaction determines which flow to run.
 * This metadata is set during transaction initialisation in OrderService / ResellerService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final OrderService   orderService;
    private final UserRepository userRepository;
    private final ObjectMapper   objectMapper;

    /**
     * Entry point — called with the raw parsed JSON node from the Paystack webhook body.
     */
    public void handle(JsonNode root) {
        String event = root.path("event").asText();
        log.info("Paystack webhook received: event={}", event);

        switch (event) {
            case "charge.success"   -> handleChargeSuccess(root.path("data"));
            case "charge.failed"    -> handleChargeFailed(root.path("data"));
            case "transfer.success" -> handleTransferSuccess(root.path("data"));
            default                 -> log.debug("Unhandled Paystack event: {}", event);
        }
    }

    // ── charge.success ────────────────────────────────────────────────────────

    private void handleChargeSuccess(JsonNode data) {
        String reference = data.path("reference").asText();
        String type      = data.path("metadata").path("type").asText("");

        log.info("charge.success: ref={} type={}", reference, type);

        switch (type) {
            case "WALLET_TOPUP" -> {
                String userIdStr = data.path("metadata").path("userId").asText("");
                UUID userId = parseUserId(userIdStr, reference);
                if (userId == null) return;

                BigDecimal amountGhc = extractAmountGhc(data);
                orderService.processTopUpWebhook(userId, amountGhc, reference);
            }

            case "GUEST_ORDER" ->
                // The order was already persisted during initiation — just fulfil it.
                    orderService.fulfilPaystackOrder(reference);

            case "RESELLER_FEE" ->
                // Registration fee paid via Paystack instead of wallet debit.
                // ResellerProfile already created as PENDING — nothing more to do here.
                    log.info("Reseller registration fee confirmed via Paystack: ref={}", reference);

            default -> {
                log.warn("charge.success with unknown metadata.type='{}' ref={}", type, reference);
                if (type.isBlank()) {
                    orderService.fulfilPaystackOrder(reference);
                }
            }
        }
    }

    // ── charge.failed ─────────────────────────────────────────────────────────

    private void handleChargeFailed(JsonNode data) {
        String reference = data.path("reference").asText();
        log.warn("charge.failed: ref={}", reference);
        // PENDING orders that never receive a success event are timed out
        // to FAILED by the background poller. No explicit action needed here.
    }

    // ── transfer.success (Phase 4 — automated payouts) ────────────────────────

    private void handleTransferSuccess(JsonNode data) {
        String transferCode = data.path("transfer_code").asText();
        log.info("transfer.success: transferCode={} (Phase 4 — not yet implemented)", transferCode);
        // Phase 4: look up Payout by transfer_code, mark as PAID.
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BigDecimal extractAmountGhc(JsonNode data) {
        long pesewas = data.path("amount").asLong(0);
        return BigDecimal.valueOf(pesewas).movePointLeft(2); // pesewas → GHS
    }

    private UUID parseUserId(String userIdStr, String reference) {
        try {
            return UUID.fromString(userIdStr);  // ← was UUID.parseLong(), which doesn't exist
        } catch (IllegalArgumentException ex) {
            log.error("Invalid userId in Paystack metadata: userId='{}' ref={}", userIdStr, reference);
            return null;
        }
    }
}