// ─────────────────────────────────────────────────────────────────────────────
// FILE: AffiliateNotActiveException.java
// Place in: com/databundleHum/OnetBundleHub/security/
// ─────────────────────────────────────────────────────────────────────────────
package com.databundleHum.OnetBundleHub.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an affiliate-only endpoint is called by a user who has not
 * activated (or has deactivated) the affiliate programme.
 * Maps to HTTP 403 Forbidden.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AffiliateNotActiveException extends RuntimeException {
    public AffiliateNotActiveException(String message) {
        super(message);
    }
}