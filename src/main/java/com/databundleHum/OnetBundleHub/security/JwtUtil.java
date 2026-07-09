package com.databundleHum.OnetBundleHub.security;

import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;

/**
 * AES-256-GCM encrypted JWT utility.
 *
 * Tokens are now JWE (encrypted), not JWS (signed-only). AES-GCM is
 * authenticated encryption: it provides confidentiality (claims such as
 * email, role, and userId are not readable by anyone who intercepts a
 * token) AND integrity (a tampered/forged ciphertext fails to decrypt) in
 * a single step, so a separate HMAC signature is no longer needed.
 *
 * Access tokens:  short-lived (15 min default), carry email + role + userId claims.
 * Refresh tokens: long-lived (7 days default), carry only email — rotated on every use.
 *
 * Required properties:
 *   app.jwt.secret              — ≥256-bit random string, stored in env / Secrets Manager.
 *                                  Hashed via SHA-256 to derive a fixed 256-bit AES key
 *                                  regardless of the raw secret's length or encoding.
 *   app.jwt.access-token-expiry-ms   (default 3_600_000   — 1 hour)
 *   app.jwt.refresh-token-expiry-ms  (default 2_592_000_000 — 30 days)
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiry-ms:3600000}") long accessTokenExpiryMs,
            @Value("${app.jwt.refresh-token-expiry-ms:2592000000}") long refreshTokenExpiryMs) {
        this.key = deriveEncryptionKey(secret);
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    /**
     * Derives a fixed 256-bit AES key from the configured secret via SHA-256.
     * AES-GCM requires an exact key size (128/192/256 bits) — hashing the raw
     * secret guarantees the right length regardless of how app.jwt.secret is
     * formatted, instead of relying on the operator to supply exactly 32 bytes.
     */
    private static SecretKey deriveEncryptionKey(String secret) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is guaranteed available on every JVM; unreachable in practice.
            throw new IllegalStateException("SHA-256 unavailable — cannot derive JWT encryption key", ex);
        }
    }

    // ── Generation ────────────────────────────────────────────────────────────

    /**
     * Generate a short-lived, encrypted access token.
     *
     * @param email  Subject (also used by Spring Security UserDetailsService)
     * @param role   e.g. "ROLE_USER", "ROLE_RESELLER", "ROLE_SUPER_ADMIN"
     * @param userId Stored as a custom claim for fast extraction in controllers.
     *               Serialized as its String form (Jackson default for UUID) —
     *               see extractUserId() below for the matching parse logic.
     */
    public String generateAccessToken(String email, String role, UUID userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .claim("userId", userId.toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiryMs))
                .encryptWith(key, Jwts.ENC.A256GCM)
                .compact();
    }

    /**
     * Generate an encrypted refresh token — contains only the subject (email).
     * A unique JTI prevents token reuse if the DB row is not yet invalidated.
     */
    public String generateRefreshToken(String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .id(UUID.randomUUID().toString())   // JTI — unique per token
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpiryMs))
                .encryptWith(key, Jwts.ENC.A256GCM)
                .compact();
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .decryptWith(key)
                .build()
                .parseEncryptedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    /**
     * Extract the userId claim as a UUID.
     *
     * JJWT serializes the "userId" claim through Jackson, which renders a UUID
     * as a JSON string — so the value always comes back as a String here,
     * never as a Long/Integer.
     */
    public UUID extractUserId(String token) {
        Object raw = extractAllClaims(token).get("userId");
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException ex) {
            log.warn("JWT userId claim is not a valid UUID: {}", raw);
            return null;
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public long getAccessTokenExpiryMs()  { return accessTokenExpiryMs;  }
    public long getRefreshTokenExpiryMs() { return refreshTokenExpiryMs; }
}
