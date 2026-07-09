package com.databundleHum.OnetBundleHub.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.*;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Extracts the Bearer JWT from the Authorization header, validates it,
 * and populates the Spring Security context so @PreAuthorize and hasRole() work.
 *
 * If no token is present the filter passes through silently — Spring Security's
 * permitAll() rules then decide whether the request is allowed.
 *
 * If a token IS present but is expired, malformed, or otherwise invalid, the
 * exception is caught and logged; the request continues unauthenticated so that
 * public endpoints still work even if the client sends a stale token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        // No token present — pass through; permitAll() rules handle public endpoints.
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (jwtUtil.isTokenValid(token)) {
                String email  = jwtUtil.extractEmail(token);
                String role   = jwtUtil.extractRole(token);   // "ROLE_USER", "ROLE_RESELLER", etc.
                UUID   userId = jwtUtil.extractUserId(token);

                if (email != null && userId != null
                        && SecurityContextHolder.getContext().getAuthentication() == null) {

                    var authorities = List.of(new SimpleGrantedAuthority(role));
                    var principal   = new UserPrincipal(userId, email);
                    var authToken   = new UsernamePasswordAuthenticationToken(
                            principal, null, authorities);
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("JWT authenticated: email={} role={} userId={}",
                            email, role, userId);

                } else if (email != null && userId == null) {
                    // Valid signature but missing/unparseable userId claim — refuse to
                    // authenticate rather than letting controllers crash downstream.
                    log.warn("JWT valid but userId claim missing/invalid for email={}", email);
                }
            }
        } catch (Exception ex) {
            // Token is present but invalid (expired, tampered, wrong key, parse error).
            // Clear any partial auth state and continue unauthenticated.
            // The request will be rejected by Spring Security if the endpoint requires auth,
            // or allowed through if it is marked permitAll().
            SecurityContextHolder.clearContext();
            log.warn("JWT validation failed for request [{} {}]: {}",
                    request.getMethod(), request.getRequestURI(), ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}