package com.databundleHum.OnetBundleHub.controllers;

import com.databundleHum.OnetBundleHub.entity.ResellerProfile;
import com.databundleHum.OnetBundleHub.repos.UserRepository;
import com.databundleHum.OnetBundleHub.services.AppUrlProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Handles inbound affiliate and reseller referral link clicks.
 *
 * Flow (architecture §4.2):
 *   1. Visitor arrives at /a/{affiliateCode} or /ref/{slug}.
 *   2. We validate the code/slug exists and is active/approved.
 *   3. Set the appropriate cookie with 30-day TTL.
 *   4. Redirect to the homepage or the reseller's storefront.
 *
 * The cookie is picked up by the frontend at registration time and sent
 * in the RegisterRequest body to AuthService.register().
 *
 * No authentication required — these are public redirect endpoints.
 *
 * Affiliate cookie spec:
 *   Name:     ref_affiliate
 *   Value:    the affiliate code, e.g. A3KP9WZQ
 *   MaxAge:   30 days (2592000 seconds)
 *   Path:     /
 *   HttpOnly: false (frontend JS needs to read it for the registration form)
 *
 * Reseller cookie spec:
 *   Name:     ref_reseller_id
 *   Value:    the store slug, e.g. kwame-data
 *   MaxAge:   30 days
 *   Path:     /
 *   HttpOnly: false
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AffiliateRedirectController {

    private static final int COOKIE_MAX_AGE_SECONDS = 30 * 24 * 60 * 60; // 30 days

    private final UserRepository userRepository;
    private final AppUrlProvider appUrlProvider;

    /**
     * Affiliate referral redirect.
     *
     * GET /a/{affiliateCode}
     *
     * Sets ref_affiliate cookie and redirects to homepage.
     * If the code is unknown or the affiliate is inactive, redirects to homepage
     * without setting the cookie (graceful degradation — no error page).
     */
    @GetMapping("/a/{affiliateCode}")
    public void affiliateRedirect(@PathVariable String affiliateCode,
                                  HttpServletResponse response) {
        String homeUrl = appUrlProvider.getBaseUrl();

        boolean valid = userRepository.findByAffiliateCode(affiliateCode)
                .map(u -> u.isAffiliate())
                .orElse(false);

        if (valid) {
            Cookie cookie = buildAffiliateCookie(affiliateCode);
            response.addCookie(cookie);
            log.info("[AFFILIATE-REDIRECT] Cookie set for code={}", affiliateCode);
        } else {
            log.warn("[AFFILIATE-REDIRECT] Unknown or inactive affiliate code={} — no cookie set",
                    affiliateCode);
        }

        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader("Location", homeUrl);
    }

    /**
     * Reseller referral redirect.
     *
     * GET /ref/{slug}
     *
     * Sets ref_reseller_id={slug} cookie and redirects to the reseller's storefront.
     * If the slug is unknown or the reseller is not approved, redirects to homepage.
     */
    @GetMapping("/ref/{slug}")
    public void resellerReferralRedirect(@PathVariable String slug,
                                         HttpServletResponse response) {
        String storeUrl = appUrlProvider.getBaseUrl() + "/store/" + slug;
        String fallback = appUrlProvider.getBaseUrl();

        boolean valid = userRepository.findByApprovedResellerSlug(
                slug,
                ResellerProfile.ResellerStatus.APPROVED
        ).isPresent();

        if (valid) {
            Cookie cookie = buildResellerCookie(slug);
            response.addCookie(cookie);
            log.info("[RESELLER-REDIRECT] Cookie set for slug={}", slug);
            response.setStatus(HttpStatus.FOUND.value());
            response.setHeader("Location", storeUrl);
        } else {
            log.warn("[RESELLER-REDIRECT] Unknown or unapproved reseller slug={} — redirecting home",
                    slug);
            response.setStatus(HttpStatus.FOUND.value());
            response.setHeader("Location", fallback);
        }
    }

    // ── Cookie builders ───────────────────────────────────────────────────────

    private Cookie buildAffiliateCookie(String affiliateCode) {
        Cookie cookie = new Cookie("ref_affiliate", affiliateCode);
        cookie.setMaxAge(COOKIE_MAX_AGE_SECONDS);
        cookie.setPath("/");
        cookie.setHttpOnly(false); // frontend JS reads it at registration
        // cookie.setSecure(true); // enable in production via application.properties
        return cookie;
    }

    private Cookie buildResellerCookie(String slug) {
        Cookie cookie = new Cookie("ref_reseller_id", slug);
        cookie.setMaxAge(COOKIE_MAX_AGE_SECONDS);
        cookie.setPath("/");
        cookie.setHttpOnly(false);
        return cookie;
    }
}