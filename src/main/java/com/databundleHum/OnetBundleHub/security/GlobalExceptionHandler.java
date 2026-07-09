package com.databundleHum.OnetBundleHub.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Central exception → HTTP response mapper.
 * All errors follow a consistent JSON shape:
 * { "status": 4xx, "error": "ERROR_CODE", "message": "Human-readable description" }
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Validation (Bean Validation) ──────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", fieldErrors);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationEx(ValidationException ex) {
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), null);
    }

    @ExceptionHandler(PriceBelowCostException.class)
    public ResponseEntity<Map<String, Object>> handlePriceBelowCost(PriceBelowCostException ex) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, "PRICE_BELOW_COST", ex.getMessage(), null);
    }

    @ExceptionHandler(MinPayoutNotMetException.class)
    public ResponseEntity<Map<String, Object>> handleMinPayout(MinPayoutNotMetException ex) {
        return body(HttpStatus.BAD_REQUEST, "MIN_PAYOUT_NOT_MET", ex.getMessage(), null);
    }

    @ExceptionHandler({BundleNotFoundException.class, InvalidNetworkException.class})
    public ResponseEntity<Map<String, Object>> handleBundle(RuntimeException ex) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_NETWORK", ex.getMessage(), null);
    }

    // ── Auth / Credentials ────────────────────────────────────────────────────

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException ex) {
        return body(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidToken(InvalidTokenException ex) {
        return body(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), null);
    }

    // ── Payment / Balance ─────────────────────────────────────────────────────

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientBalance(InsufficientBalanceException ex) {
        return body(HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_BALANCE", ex.getMessage(), null);
    }

    // ── Forbidden ─────────────────────────────────────────────────────────────

    @ExceptionHandler({ForbiddenException.class, AccountDeactivatedException.class,
            ResellerNotApprovedException.class, AffiliateNotActiveException.class})
    public ResponseEntity<Map<String, Object>> handleForbidden(RuntimeException ex) {
        return body(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), null);
    }

    // ── Not Found ─────────────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), null);
    }

    // ── Conflict ─────────────────────────────────────────────────────────────

    @ExceptionHandler({ConflictException.class, DuplicateApplicationException.class,
            AlreadyResellerException.class, DuplicateReferenceException.class})
    public ResponseEntity<Map<String, Object>> handleConflict(RuntimeException ex) {
        return body(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), null);
    }

    // ── Upstream ─────────────────────────────────────────────────────────────

    @ExceptionHandler(UpstreamApiException.class)
    public ResponseEntity<Map<String, Object>> handleUpstream(UpstreamApiException ex) {
        log.error("Upstream API error: {}", ex.getMessage());
        return body(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", ex.getMessage(), null);
    }

    // ── Catch-all ─────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again.", null);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String error,
                                                     String message, Object details) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        if (details != null) body.put("details", details);
        return ResponseEntity.status(status).body(body);
    }
}