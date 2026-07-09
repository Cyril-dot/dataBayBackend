package com.databundleHum.OnetBundleHub.services;

import com.databundleHum.OnetBundleHub.dtos.response.BigDreamsBundleResponse;
import com.databundleHum.OnetBundleHub.entity.Order;
import com.databundleHum.OnetBundleHub.repos.OrderRepository;
import com.databundleHum.OnetBundleHub.security.UpstreamApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Facade over the Big Dreams Data developer API.
 *
 * API reference  : https://qrzjkrkawcdoaggblvjc.supabase.co/functions/v1/developer-api
 * Authentication : x-api-key header (set globally on the WebClient bean in AppConfig)
 *
 * All operations use POST to the same endpoint, differentiated by the
 * "action" field in the JSON body:
 *   place_order      — purchase and deliver a data bundle
 *   check_balance     — diagnostic wallet balance check
 *   get_bundles       — fetch available bundles with our custom API pricing applied
 *   get_transactions  — paginated transaction history, WITH per-transaction
 *                        terminal status (e.g. "completed", "failed") — this
 *                        is what we use to confirm real delivery, since there
 *                        is no webhook and no dedicated status-lookup endpoint.
 *
 * Network key mapping (our internal enum → Big Dreams API value, for place_order):
 *   MTN        → mtn
 *   AT_PREMIUM → ishare
 *   AIRTELTIGO → ishare   (AirtelTigo is the "iShare" product upstream)
 *   TELECEL    → telecel
 *   AT_BIGTIME → (unsupported — throws UpstreamApiException)
 *
 * NOTE: the Big Dreams "get_bundles" action expects network filter values of
 * "mtn" | "telecel" | "airteltigo" — a DIFFERENT string ("airteltigo") than
 * the "place_order" action uses for the same network ("ishare"). These are
 * two separate vocabularies in the same API. Do NOT reuse NETWORK_KEY_MAP
 * values (place_order-only) when calling fetchAvailableBundles(...); pass the
 * literal "airteltigo" string as the network filter for that endpoint instead.
 *
 * Delivery confirmation strategy:
 *   place_order returns "processing" synchronously — this only means Big
 *   Dreams accepted and queued the order, NOT that the bundle has landed on
 *   the recipient's SIM. Actual delivery time is NOT fixed (it can be
 *   seconds or much longer depending on network conditions upstream), so we
 *   never guess a completion time or timeout-fail a PENDING order on a
 *   clock. Instead:
 *
 *     1. purchase() sets the order to PENDING immediately after Big Dreams
 *        accepts it (success: true), storing dbhPurchaseId (transaction_id)
 *        and dbhReference (reference, e.g. "order-25").
 *     2. checkDeliveryStatus() polls get_transactions periodically and looks
 *        up each PENDING order's reference string against the "order_id"
 *        field of transactions returned there. When Big Dreams reports that
 *        transaction's status as "completed", we promote the order to
 *        COMPLETED. If Big Dreams reports "failed"/"declined", we mark the
 *        order FAILED (and a refund should be triggered from there).
 *     3. Orders with no match yet are left PENDING indefinitely and simply
 *        re-checked next cycle — we do not invent a failure just because
 *        time has passed, since the wallet was already charged upstream and
 *        a false FAILED could cause a duplicate refund / duplicate
 *        purchase later.
 *
 * IMPORTANT — matching key for delivery confirmation:
 *   Big Dreams' get_transactions response does NOT include a numeric "id"
 *   field on each transaction — it identifies transactions by the string
 *   "order_id" we originally sent in place_order (e.g. "order-25"). The
 *   numeric "transaction_id" (stored as dbhPurchaseId) is only present in
 *   the place_order response, not in get_transactions, so it CANNOT be used
 *   to join against the transaction feed. We match on dbhReference (the
 *   "order-{id}" string) instead. An earlier version of this class tried to
 *   index transactions by a nonexistent "id" field, which meant the map was
 *   always empty and orders stayed PENDING forever even after upstream
 *   reported them "completed" — see get_transactions raw body logs showing
 *   "status":"completed" entries that never got picked up.
 *
 * IMPORTANT — per the Big Dreams API docs, get_transactions is PAGINATED and
 *   defaults to 10 records; we explicitly request limit=TRANSACTIONS_PAGE_SIZE
 *   (50), offset=0 — i.e. only the 50 MOST RECENT transactions across the
 *   whole account (purchases, top-ups, everything — "type" isn't filtered).
 *   If the account processes more than 50 transactions between poller runs,
 *   an older PENDING order could scroll off this page before ever matching.
 *
 * IMPORTANT — Order.OrderStatus must include a COMPLETED value:
 *   enum OrderStatus { PENDING, VERIFIED, COMPLETED, FAILED }
 *
 * IMPORTANT — the Postgres check constraint on orders.status MUST be kept in
 *   sync with the Java enum above. A drift here (constraint missing a value
 *   the code tries to write) will silently abort the enclosing transaction
 *   and cause every subsequent query in that transaction to fail with
 *   "current transaction is aborted" — this has happened before. If you add
 *   a new OrderStatus value, migrate the DB constraint in the same change:
 *
 *     ALTER TABLE orders DROP CONSTRAINT orders_status_check;
 *     ALTER TABLE orders ADD CONSTRAINT orders_status_check
 *       CHECK (status IN ('PENDING', 'VERIFIED', 'COMPLETED', 'FAILED'));
 *
 * IMPORTANT — set in application.properties:
 *   bigdreams.api-key=bh_live_your_key_here
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BigDreamsService {

    private static final int  MAX_RETRIES            = 3;
    private static final long RETRY_DELAY_MS          = 2_000L;
    private static final int  TRANSACTIONS_PAGE_SIZE  = 50;

    private static final Map<String, String> NETWORK_KEY_MAP = Map.of(
            "MTN",        "mtn",
            "AT_PREMIUM", "ishare",
            "AIRTELTIGO", "ishare",
            "TELECEL",    "telecel"
            // AT_BIGTIME intentionally omitted — not available on Big Dreams Data
    );

    @Qualifier("bigDreamsWebClient")
    private final WebClient       bigDreamsWebClient;
    private final OrderRepository orderRepository;
    private final ObjectMapper    objectMapper = new ObjectMapper();

    // ── Network key resolution ────────────────────────────────────────────────

    private String resolveNetworkKey(Order order) {
        String internalKey = order.getNetwork().name();
        log.debug("[BIGDREAMS] Resolving networkKey: internalKey={}", internalKey);
        String apiKey = NETWORK_KEY_MAP.get(internalKey);
        if (apiKey == null) {
            log.error("[BIGDREAMS] No networkKey mapping for internalKey={} — available={}",
                    internalKey, NETWORK_KEY_MAP.keySet());
            throw new UpstreamApiException(
                    "No Big Dreams Data networkKey mapping configured for network: " + internalKey
                            + ". Supported networks: " + NETWORK_KEY_MAP.keySet());
        }
        log.debug("[BIGDREAMS] networkKey resolved: {} → {}", internalKey, apiKey);
        return apiKey;
    }

    // ── Fetch available bundles (admin pricing helper) ────────────────────────

    /**
     * Fetch all available bundles from the Big Dreams Data API, with our
     * account's custom pricing applied (if configured in the Big Dreams dashboard).
     *
     * @param networkFilter  optional API network key to filter results
     *                       ("mtn", "telecel", "airteltigo"). Pass null for all networks.
     * @return               list of bundle items with our buying price, or empty list on error.
     */
    public List<BigDreamsBundleResponse> fetchAvailableBundles(String networkFilter) {
        log.info("[BIGDREAMS] Fetching available bundles — networkFilter={}", networkFilter);
        long startMs = System.currentTimeMillis();

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "get_bundles");
        if (networkFilter != null && !networkFilter.isBlank()) {
            payload.put("network", networkFilter.toLowerCase());
        }

        try {
            String rawBody = bigDreamsWebClient.post()
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            long durationMs = System.currentTimeMillis() - startMs;
            log.debug("[BIGDREAMS] get_bundles response in {}ms — rawBody=[{}]", durationMs, rawBody);

            if (rawBody == null || rawBody.isBlank()) {
                log.warn("[BIGDREAMS] get_bundles returned empty body");
                return List.of();
            }

            //noinspection unchecked
            Map<String, Object> response = objectMapper.readValue(rawBody, Map.class);

            if (!Boolean.TRUE.equals(response.get("success"))) {
                log.warn("[BIGDREAMS] get_bundles non-success — response={}", response);
                return List.of();
            }

            //noinspection unchecked
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) {
                log.warn("[BIGDREAMS] get_bundles missing 'data' field");
                return List.of();
            }

            //noinspection unchecked
            List<Map<String, Object>> bundles =
                    (List<Map<String, Object>>) data.get("bundles");
            if (bundles == null || bundles.isEmpty()) {
                log.info("[BIGDREAMS] get_bundles returned zero bundles");
                return List.of();
            }

            List<BigDreamsBundleResponse> result = new ArrayList<>();
            for (Map<String, Object> b : bundles) {
                try {
                    BigDreamsBundleResponse item = BigDreamsBundleResponse.builder()
                            .id(toLong(b.get("id")))
                            .network(toString(b.get("network")))
                            .size(toString(b.get("size")))                     // e.g. "5GB"
                            .sizeGb(toInt(b.get("size_gb")))                   // e.g. 5
                            .buyingPriceGhc(toBigDecimal(b.get("price")))      // what we pay
                            .validity(toString(b.get("validity")))             // e.g. "30 Days"
                            .hasCustomPrice(toBoolean(b.get("has_custom_price")))
                            .build();
                    result.add(item);
                } catch (Exception ex) {
                    log.warn("[BIGDREAMS] Failed to parse bundle entry: {} — error={}",
                            b, ex.getMessage());
                }
            }

            log.info("[BIGDREAMS] get_bundles: {} bundle(s) fetched in {}ms",
                    result.size(), System.currentTimeMillis() - startMs);
            return result;

        } catch (Exception ex) {
            log.warn("[BIGDREAMS] get_bundles failed — type={} error={}",
                    ex.getClass().getSimpleName(), ex.getMessage());
            return List.of();
        }
    }

    /**
     * Convenience overload — fetch all bundles across all networks.
     */
    public List<BigDreamsBundleResponse> fetchAvailableBundles() {
        return fetchAvailableBundles(null);
    }

    // ── Purchase bundle ───────────────────────────────────────────────────────

    /**
     * Submit a bundle purchase to the Big Dreams Data API.
     *
     * Retries up to MAX_RETRIES times with RETRY_DELAY_MS between attempts.
     * On acceptance → order saved as PENDING (delivery is asynchronous and
     * has no fixed duration — see checkDeliveryStatus() for confirmation).
     * On exhausted retries → order saved as FAILED, UpstreamApiException thrown
     * so the caller (OrderService) can issue a wallet refund.
     *
     * Non-recoverable rejections (fractional bundle size, insufficient
     * upstream balance, invalid network mapping) fail fast without burning
     * through all MAX_RETRIES, since retrying won't change the outcome.
     *
     * Runs in REQUIRES_NEW so a failure here never causes
     * UnexpectedRollbackException in the calling OrderService transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purchase(Order order) {
        long startMs = System.currentTimeMillis();

        log.info("[BIGDREAMS] ═══════════════════════════════════════════════════");
        log.info("[BIGDREAMS] PURCHASE START");
        log.info("[BIGDREAMS]   orderId   = {}", order.getId());
        log.info("[BIGDREAMS]   recipient = {}", order.getPhoneNumber());
        log.info("[BIGDREAMS]   network   = {}", order.getNetwork());
        log.info("[BIGDREAMS]   capacityGb= {}", order.getCapacityGb());
        log.info("[BIGDREAMS]   timestamp = {}", Instant.now());
        log.info("[BIGDREAMS] ═══════════════════════════════════════════════════");

        String networkKey  = resolveNetworkKey(order);
        int    capacityInt = order.getCapacityGb().stripTrailingZeros().intValue();

        Map<String, Object> payload = new HashMap<>();
        payload.put("action",       "place_order");
        payload.put("network",      networkKey);
        payload.put("recipient",    order.getPhoneNumber());
        payload.put("package_size", capacityInt);
        payload.put("order_id",     "order-" + order.getId());

        log.info("[BIGDREAMS] Payload → network={} recipient={} package_size={} order_id=order-{}",
                networkKey, order.getPhoneNumber(), capacityInt, order.getId());

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            long attemptStart = System.currentTimeMillis();
            log.info("[BIGDREAMS] ─────────────────────────────────────────────────");
            log.info("[BIGDREAMS] Attempt {}/{} — orderId={}", attempt, MAX_RETRIES, order.getId());

            try {
                String rawBody = bigDreamsWebClient.post()
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                long durationMs = System.currentTimeMillis() - attemptStart;
                log.info("[BIGDREAMS] Response in {}ms — orderId={}", durationMs, order.getId());
                log.info("[BIGDREAMS] Raw body: [{}]", rawBody);

                if (rawBody == null || rawBody.isBlank()) {
                    throw new UpstreamApiException(
                            "Big Dreams Data returned empty body for orderId=" + order.getId()
                                    + " (attempt " + attempt + "/" + MAX_RETRIES + ")");
                }

                Map<String, Object> response;
                try {
                    //noinspection unchecked
                    response = objectMapper.readValue(rawBody, Map.class);
                } catch (Exception parseEx) {
                    throw new UpstreamApiException(
                            "Big Dreams Data returned unparseable body for orderId=" + order.getId()
                                    + ": " + rawBody);
                }

                Object successRaw = response.get("success");
                boolean success = Boolean.TRUE.equals(successRaw);
                if (!success) {
                    String errMsg = response.getOrDefault("message",
                            response.getOrDefault("error", "Unknown Big Dreams error")).toString();

                    // Fail fast on non-recoverable errors — retrying won't help.
                    String lowerErr = errMsg.toLowerCase();
                    boolean nonRecoverable = lowerErr.contains("insufficient balance")
                            || lowerErr.contains("invalid phone number")
                            || lowerErr.contains("bundle not found")
                            || lowerErr.contains("invalid api key");

                    if (nonRecoverable) {
                        log.error("[BIGDREAMS] Non-recoverable rejection — orderId={} error={}",
                                order.getId(), errMsg);
                        order.setStatus(Order.OrderStatus.FAILED);
                        orderRepository.save(order);
                        throw new UpstreamApiException("Big Dreams Data purchase rejected: " + errMsg);
                    }

                    throw new UpstreamApiException("Big Dreams Data purchase rejected: " + errMsg);
                }

                //noinspection unchecked
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data == null) {
                    throw new UpstreamApiException(
                            "Big Dreams Data response missing 'data' field for orderId=" + order.getId());
                }

                Object transactionId = data.get("transaction_id");
                Object reference     = data.get("reference");
                Object apiStatus     = data.get("status");
                Object newBalance    = data.get("new_balance");
                Object bundle        = data.get("bundle");
                Object validity      = data.get("validity");

                log.info("[BIGDREAMS] ✔ Purchase accepted (queued upstream, not yet delivered):");
                log.info("[BIGDREAMS]   orderId            = {}", order.getId());
                log.info("[BIGDREAMS]   API transaction_id = {}", transactionId);
                log.info("[BIGDREAMS]   API reference      = {}", reference);
                log.info("[BIGDREAMS]   API status         = {} (\"processing\" is normal)", apiStatus);
                log.info("[BIGDREAMS]   bundle             = {} {}", bundle, validity);
                log.info("[BIGDREAMS]   new wallet balance = {}", newBalance);
                log.info("[BIGDREAMS]   total elapsed      = {}ms", System.currentTimeMillis() - startMs);

                order.setDbhReference(reference != null ? reference.toString() : null);
                order.setDbhPurchaseId(transactionId != null
                        ? Long.parseLong(transactionId.toString()) : null);
                // Delivery is asynchronous with no fixed duration — the order stays
                // PENDING until checkDeliveryStatus() confirms it against
                // get_transactions. Do NOT set COMPLETED here.
                order.setStatus(Order.OrderStatus.PENDING);
                orderRepository.save(order);

                log.info("[BIGDREAMS] Order saved as PENDING (awaiting delivery confirmation) — orderId={}",
                        order.getId());
                log.info("[BIGDREAMS] ═══════════════════════════════════════════════════");
                return;

            } catch (WebClientResponseException ex) {
                lastException = ex;
                log.warn("[BIGDREAMS] HTTP {} on attempt {}/{} — orderId={} reason=\"{}\"",
                        ex.getStatusCode(), attempt, MAX_RETRIES, order.getId(), ex.getStatusText());
                if (ex.getStatusCode().value() == 401) {
                    log.error("[BIGDREAMS] 401 Unauthorized — check bigdreams.api-key");
                    break;
                }
            } catch (UpstreamApiException ex) {
                lastException = ex;
                log.warn("[BIGDREAMS] API error on attempt {}/{} — orderId={} error={}",
                        attempt, MAX_RETRIES, order.getId(), ex.getMessage());
                if (order.getStatus() == Order.OrderStatus.FAILED) {
                    // Already marked FAILED above (non-recoverable) — stop retrying.
                    throw ex;
                }
            } catch (Exception ex) {
                lastException = ex;
                log.warn("[BIGDREAMS] Unexpected error on attempt {}/{} — orderId={} type={} error={}",
                        attempt, MAX_RETRIES, order.getId(),
                        ex.getClass().getSimpleName(), ex.getMessage());
            }

            if (attempt < MAX_RETRIES) {
                log.info("[BIGDREAMS] Waiting {}ms before retry — orderId={}", RETRY_DELAY_MS, order.getId());
                sleepQuietly(RETRY_DELAY_MS);
            }
        }

        // All retries exhausted
        log.error("[BIGDREAMS] PURCHASE FAILED — ALL {} RETRIES EXHAUSTED — orderId={}",
                MAX_RETRIES, order.getId());
        order.setStatus(Order.OrderStatus.FAILED);
        orderRepository.save(order);

        throw new UpstreamApiException(
                "Bundle purchase failed after " + MAX_RETRIES + " attempts. OrderId=" + order.getId());
    }

    // ── Delivery confirmation poller ──────────────────────────────────────────

    /**
     * Every 60 seconds: for every PENDING order, check whether Big Dreams'
     * get_transactions feed now reports that transaction as delivered.
     *
     * There is intentionally NO fixed timeout here. Delivery duration on the
     * upstream network is not fixed — it can vary a lot — so an order simply
     * stays PENDING and gets re-checked on the next cycle for as long as it
     * takes. We only ever move an order out of PENDING when Big Dreams itself
     * reports a terminal status ("completed" or "failed"/"declined") for that
     * order. If an order has had no matching transaction for an unusually
     * long time, we just log it for manual follow-up — we never auto-fail it
     * on a clock, since the wallet was already charged upstream and a wrong
     * FAILED verdict risks a duplicate refund or duplicate order.
     *
     * FIX: matching is done on the "order_id" string (our own
     * order.getDbhReference(), e.g. "order-25") because Big Dreams'
     * get_transactions response has NO numeric "id" field on each
     * transaction — only "order_id". Matching on dbhPurchaseId (the numeric
     * transaction_id, only present in the place_order response) against a
     * nonexistent "id" field silently indexed zero transactions every cycle,
     * so orders never left PENDING even when upstream reported "completed".
     */
    @Scheduled(fixedDelay = 60_000L)
    @Transactional
    public void checkDeliveryStatus() {
        List<Order> pendingOrders = orderRepository.findByStatus(Order.OrderStatus.PENDING);

        if (pendingOrders.isEmpty()) {
            log.debug("[BIGDREAMS] Delivery poller: no PENDING orders");
            return;
        }

        log.info("[BIGDREAMS] Delivery poller: checking {} PENDING order(s)", pendingOrders.size());

        Map<String, String> statusByOrderId = fetchTransactionStatuses(TRANSACTIONS_PAGE_SIZE);
        if (statusByOrderId.isEmpty()) {
            log.warn("[BIGDREAMS] Delivery poller: get_transactions returned nothing usable this cycle");
            return;
        }

        for (Order order : pendingOrders) {
            // Prefer the reference we stored at purchase time; fall back to
            // reconstructing "order-{id}" if it's somehow missing.
            String orderKey = order.getDbhReference() != null
                    ? order.getDbhReference()
                    : "order-" + order.getId();

            String upstreamStatus = statusByOrderId.get(orderKey);
            if (upstreamStatus == null) {
                log.debug("[BIGDREAMS] orderId={} orderKey={} — no matching transaction this cycle, still PENDING",
                        order.getId(), orderKey);
                continue;
            }

            switch (upstreamStatus.toLowerCase()) {
                case "completed", "success", "delivered" -> {
                    order.setStatus(Order.OrderStatus.COMPLETED);
                    orderRepository.save(order);
                    log.info("[BIGDREAMS] ✔ Order confirmed DELIVERED — orderId={} orderKey={} upstreamStatus={}",
                            order.getId(), orderKey, upstreamStatus);
                }
                case "failed", "declined", "rejected" -> {
                    order.setStatus(Order.OrderStatus.FAILED);
                    orderRepository.save(order);
                    log.warn("[BIGDREAMS] ✘ Order confirmed FAILED upstream — orderId={} orderKey={} upstreamStatus={} (refund needed)",
                            order.getId(), orderKey, upstreamStatus);
                    // TODO: trigger wallet refund here if OrderService doesn't already
                    // handle FAILED transitions elsewhere.
                }
                default -> log.debug("[BIGDREAMS] orderId={} orderKey={} upstream status still '{}' — waiting",
                        order.getId(), orderKey, upstreamStatus);
            }
        }

        log.info("[BIGDREAMS] Delivery poller: cycle complete");
    }

    /**
     * Fetch recent transactions from Big Dreams and index them by
     * order_id → status, for matching against our PENDING orders'
     * dbhReference (e.g. "order-25").
     *
     * Per the Big Dreams docs, get_transactions supports "limit" and
     * "offset" for pagination and defaults to 10 records if limit is
     * omitted — we always pass explicit limit/offset. The response also
     * carries a "count" (and sometimes "total") count alongside
     * data.transactions, which we log so we can tell whether the account
     * has transactions at all vs. this specific call/page just isn't
     * returning them.
     *
     * NOTE: each transaction object has NO numeric "id" field — only a
     * string "order_id" (the same value we sent as "order_id" in
     * place_order). Do not attempt to index by "id"; it doesn't exist in
     * this response and will silently produce an empty map.
     */
    private Map<String, String> fetchTransactionStatuses(int limit) {
        Map<String, String> result = new HashMap<>();
        try {
            Map<String, Object> payload = Map.of(
                    "action", "get_transactions",
                    "limit", limit,
                    "offset", 0
            );

            String rawBody = bigDreamsWebClient.post()
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Log the raw response so we can see exactly what Big Dreams sent,
            // rather than only inferring from downstream counts.
            log.info("[BIGDREAMS] get_transactions raw body: [{}]", rawBody);

            if (rawBody == null || rawBody.isBlank()) {
                log.warn("[BIGDREAMS] get_transactions returned empty body");
                return result;
            }

            //noinspection unchecked
            Map<String, Object> response = objectMapper.readValue(rawBody, Map.class);
            if (!Boolean.TRUE.equals(response.get("success"))) {
                log.warn("[BIGDREAMS] get_transactions non-success — response={}", response);
                return result;
            }

            //noinspection unchecked
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) {
                log.warn("[BIGDREAMS] get_transactions missing 'data' field — response={}", response);
                return result;
            }

            //noinspection unchecked
            List<Map<String, Object>> txns = (List<Map<String, Object>>) data.get("transactions");
            Object countField = data.get("count");
            Object totalField = data.get("total");

            if (txns == null || txns.isEmpty()) {
                log.warn("[BIGDREAMS] get_transactions returned zero transactions — " +
                        "count={} total={} fullData={}", countField, totalField, data);
                return result;
            }

            for (Map<String, Object> t : txns) {
                String orderId = toString(t.get("order_id")); // e.g. "order-25"; null for top-ups
                String status  = toString(t.get("status"));
                if (orderId != null && status != null) {
                    result.put(orderId, status);
                }
            }

            log.debug("[BIGDREAMS] get_transactions: indexed {} of {} transaction(s) (total={})",
                    result.size(), countField, totalField);

        } catch (Exception ex) {
            log.warn("[BIGDREAMS] get_transactions poll failed — type={} error={}",
                    ex.getClass().getSimpleName(), ex.getMessage());
        }
        return result;
    }

    // ── Webhook stub ──────────────────────────────────────────────────────────

    @Transactional
    public void handleWebhook(Map<String, Object> payload) {
        log.info("[BIGDREAMS] Webhook stub — payload={}", payload);
    }

    // ── Diagnostic: wallet balance ────────────────────────────────────────────

    /**
     * Fetch the current Big Dreams Data wallet balance.
     * Returns "unavailable" on any error.
     */
    public String fetchWalletBalance() {
        log.debug("[BIGDREAMS] Fetching wallet balance");
        try {
            String rawBody = bigDreamsWebClient.post()
                    .bodyValue(Map.of("action", "check_balance"))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (rawBody == null || rawBody.isBlank()) return "unavailable";

            //noinspection unchecked
            Map<String, Object> response = objectMapper.readValue(rawBody, Map.class);
            if (!Boolean.TRUE.equals(response.get("success"))) return "unavailable";

            //noinspection unchecked
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) return "unavailable";

            Object balance  = data.get("wallet_balance");
            Object currency = data.get("currency");
            String result = (balance != null ? balance.toString() : "?")
                    + (currency != null ? " " + currency : "");

            log.info("[BIGDREAMS] Wallet balance: {}", result);
            return result;

        } catch (Exception ex) {
            log.warn("[BIGDREAMS] Balance check failed: {}", ex.getMessage());
            return "unavailable";
        }
    }

    // ── Private type-conversion helpers ──────────────────────────────────────

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        return Long.parseLong(o.toString());
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        return Integer.parseInt(o.toString());
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        return new BigDecimal(o.toString());
    }

    private String toString(Object o) {
        return o != null ? o.toString() : null;
    }

    private boolean toBoolean(Object o) {
        return Boolean.TRUE.equals(o);
    }

    private void sleepQuietly(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}