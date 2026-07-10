package com.databundleHum.OnetBundleHub.controllers;

import com.databundleHum.OnetBundleHub.services.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhook", description = "Paystack webhook receiver")
public class WebhookController {

    private final OrderService orderService;

    @Value("${paystack.secret-key}")
    private String paystackSecretKey;

    /**
     * Must match OrderService.PAYSTACK_CHARGE_RATE. Only used as a FALLBACK
     * to back a base amount out of the raw charged amount if "baseAmountGhc"
     * is ever missing from metadata — the normal path always uses the
     * metadata value directly, since that's the exact figure the customer
     * agreed to top up.
     */
    private static final BigDecimal PAYSTACK_CHARGE_RATE = new BigDecimal("0.10");

    @PostMapping("/paystack")
    @Operation(summary = "Paystack webhook — charge.success handler")
    public ResponseEntity<Void> handlePaystack(
            @RequestHeader(value = "x-paystack-signature", required = false) String signature,
            @RequestBody Map<String, Object> payload) {

        // ── STEP 1: Log that we received anything at all ──────────────────────
        log.info("[WEBHOOK] ✅ Request received at /api/webhooks/paystack");
        log.info("[WEBHOOK] Signature header present: {}", signature != null ? "YES (length=" + signature.length() + ")" : "NO - NULL");
        log.info("[WEBHOOK] Raw payload keys: {}", payload.keySet());
        log.info("[WEBHOOK] Secret key prefix being used: {}", paystackSecretKey != null ? paystackSecretKey.substring(0, Math.min(12, paystackSecretKey.length())) + "..." : "NULL");

        // ── STEP 2: Signature validation ──────────────────────────────────────
        log.info("[WEBHOOK] Starting signature validation...");
        boolean signatureValid = isValidSignature(payload, signature);
        log.info("[WEBHOOK] Signature valid: {}", signatureValid);

        if (!signatureValid) {
            log.warn("[WEBHOOK] ❌ Signature validation FAILED — returning 401");
            log.warn("[WEBHOOK] Received signature: {}", signature);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("[WEBHOOK] ✅ Signature validation passed");

        // ── STEP 3: Extract event type ────────────────────────────────────────
        String event = (String) payload.get("event");
        log.info("[WEBHOOK] Event type: {}", event);

        if (!"charge.success".equals(event)) {
            log.info("[WEBHOOK] Ignoring non-charge event: {} — returning 200", event);
            return ResponseEntity.ok().build();
        }

        // ── STEP 4: Extract data block ────────────────────────────────────────
        Map<String, Object> data = extractData(payload);
        if (data == null) {
            log.warn("[WEBHOOK] ❌ 'data' field missing from payload — returning 400");
            return ResponseEntity.badRequest().build();
        }
        log.info("[WEBHOOK] Data block keys: {}", data.keySet());

        // ── STEP 5: Extract reference ─────────────────────────────────────────
        String reference = (String) data.get("reference");
        log.info("[WEBHOOK] Reference: {}", reference);
        if (reference == null || reference.isBlank()) {
            log.warn("[WEBHOOK] ❌ Reference is null or blank — returning 400");
            return ResponseEntity.badRequest().build();
        }

        // ── STEP 6: Extract metadata ──────────────────────────────────────────
        Map<String, Object> meta = extractMeta(data);
        log.info("[WEBHOOK] Metadata present: {}", meta != null ? "YES, keys=" + meta.keySet() : "NO - NULL");
        String type = meta != null ? (String) meta.get("type") : null;
        log.info("[WEBHOOK] Transaction type from metadata: {}", type);

        // ── STEP 7: Extract amount ────────────────────────────────────────────
        Object rawAmount = data.get("amount");
        log.info("[WEBHOOK] Raw amount from Paystack: {}", rawAmount);

        // ── STEP 8: Route and process ─────────────────────────────────────────
        log.info("[WEBHOOK] Routing: ref={} type={}", reference, type);
        try {
            if ("WALLET_TOPUP".equals(type)) {
                String userIdStr = (String) meta.get("userId");
                log.info("[WEBHOOK] userId from metadata: {}", userIdStr);
                UUID userId = UUID.fromString(userIdStr);

                // IMPORTANT: the customer was charged base × 1.10 via Paystack.
                // We must credit the wallet with the BASE amount (what they
                // asked to top up), not the raw charged amount — otherwise
                // they get a free 10% bonus on every top-up.
                BigDecimal chargedAmountGhc = extractAmountGhc(data);
                BigDecimal baseAmountGhc = extractBaseAmountGhc(meta, chargedAmountGhc, reference);

                log.info("[WEBHOOK] Processing WALLET_TOPUP: userId={} chargedAmount=GHS{} " +
                                "creditAmount=GHS{} ref={}",
                        userId, chargedAmountGhc, baseAmountGhc, reference);
                orderService.processTopUpWebhook(userId, baseAmountGhc, reference);
                log.info("[WEBHOOK] ✅ WALLET_TOPUP credited successfully: userId={} amount=GHS{} ref={}",
                        userId, baseAmountGhc, reference);

            } else if ("GUEST_ORDER".equals(type)) {
                log.info("[WEBHOOK] Processing GUEST_ORDER: ref={}", reference);
                orderService.fulfilPaystackOrder(reference);
                log.info("[WEBHOOK] ✅ GUEST_ORDER fulfilled: ref={}", reference);

            } else {
                log.warn("[WEBHOOK] ⚠️ Unknown or null transaction type='{}' ref={} — ignoring", type, reference);
                log.warn("[WEBHOOK] Full metadata dump: {}", meta);
            }
        } catch (Exception ex) {
            log.error("[WEBHOOK] ❌ Processing error: ref={} type={} error={}", reference, type, ex.getMessage(), ex);
        }

        log.info("[WEBHOOK] ✅ Returning 200 to Paystack for ref={}", reference);
        return ResponseEntity.ok().build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean isValidSignature(Map<String, Object> payload, String signature) {
        if (signature == null || signature.isBlank()) {
            log.warn("[WEBHOOK-SIG] ❌ Signature is null or blank");
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(
                    paystackSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));

            String body = toJsonString(payload);
            log.debug("[WEBHOOK-SIG] JSON body used for HMAC (first 200 chars): {}",
                    body.length() > 200 ? body.substring(0, 200) + "..." : body);

            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);

            log.info("[WEBHOOK-SIG] Computed HMAC (first 20 chars): {}...", computed.substring(0, 20));
            log.info("[WEBHOOK-SIG] Received signature (first 20 chars): {}...", signature.substring(0, Math.min(20, signature.length())));

            boolean match = computed.equalsIgnoreCase(signature);
            log.info("[WEBHOOK-SIG] HMAC match: {}", match);
            return match;

        } catch (Exception ex) {
            log.error("[WEBHOOK-SIG] ❌ Exception during signature validation: {}", ex.getMessage(), ex);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(Map<String, Object> payload) {
        Object data = payload.get("data");
        return (data instanceof Map) ? (Map<String, Object>) data : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMeta(Map<String, Object> data) {
        Object meta = data.get("metadata");
        return (meta instanceof Map) ? (Map<String, Object>) meta : null;
    }

    private BigDecimal extractAmountGhc(Map<String, Object> data) {
        Object raw = data.get("amount");
        if (raw == null) {
            log.warn("[WEBHOOK] Amount is null, defaulting to 0");
            return BigDecimal.ZERO;
        }
        BigDecimal result = new BigDecimal(raw.toString()).divide(BigDecimal.valueOf(100));
        log.info("[WEBHOOK] Amount converted: {}pesewas → GHS{}", raw, result);
        return result;
    }

    /**
     * Reads the ORIGINAL top-up amount the customer requested (before the
     * 10% Paystack processing charge) from metadata.baseAmountGhc, which
     * OrderService.initiateTopUp() stashed there at initiation time.
     *
     * Falls back to backing it out of the charged amount only if metadata
     * is somehow missing it — this should never happen in normal operation
     * and is logged loudly if it does, since it means initiateTopUp()
     * and this webhook have drifted out of sync.
     */
    private BigDecimal extractBaseAmountGhc(Map<String, Object> meta,
                                            BigDecimal chargedAmountGhc,
                                            String reference) {
        Object rawBase = meta != null ? meta.get("baseAmountGhc") : null;

        if (rawBase != null) {
            try {
                return new BigDecimal(rawBase.toString()).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException ex) {
                log.error("[WEBHOOK] ❌ Unparseable baseAmountGhc='{}' ref={} — falling back",
                        rawBase, reference);
            }
        } else {
            log.warn("[WEBHOOK] ⚠️ metadata.baseAmountGhc missing ref={} — falling back to " +
                    "back-calculating from charged amount", reference);
        }

        return chargedAmountGhc
                .divide(BigDecimal.ONE.add(PAYSTACK_CHARGE_RATE), 2, RoundingMode.HALF_UP);
    }

    private String toJsonString(Map<String, Object> payload) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(payload);
            log.debug("[WEBHOOK-SIG] Serialized payload length: {} chars", json.length());
            return json;
        } catch (Exception ex) {
            log.warn("[WEBHOOK-SIG] ⚠️ JSON serialisation fallback used: {}", ex.getMessage());
            return payload.toString();
        }
    }
}