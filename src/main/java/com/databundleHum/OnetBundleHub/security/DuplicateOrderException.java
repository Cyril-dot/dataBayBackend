package com.databundleHum.OnetBundleHub.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a wallet order is submitted within the duplicate-detection window
 * (30 seconds) for an identical userId + phone + network + capacity combination.
 *
 * Maps to HTTP 409 Conflict so the frontend can surface a clear message to the
 * user rather than showing a generic error.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateOrderException extends RuntimeException {
    public DuplicateOrderException(String message) {
        super(message);
    }
}