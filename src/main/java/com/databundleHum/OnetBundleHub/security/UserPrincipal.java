package com.databundleHum.OnetBundleHub.security;

import java.util.UUID;

/**
 * Authenticated principal attached to the SecurityContext by JwtAuthFilter.
 *
 * Carries both the user's UUID (for DB lookups / ownership checks) and email
 * (for display, logging, or UserDetailsService-style flows), so controllers
 * never need to reconstruct one from the other.
 */
public record UserPrincipal(UUID userId, String email) {
}