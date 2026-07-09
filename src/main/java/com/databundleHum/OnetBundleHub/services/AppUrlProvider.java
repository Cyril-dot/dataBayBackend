package com.databundleHum.OnetBundleHub.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provides the application base URL for building referral links, store URLs,
 * and redirect targets.
 *
 * Set in application.properties:
 *   app.base-url=https://yourdomain.com
 *
 * Used by:
 *   - AffiliateService        (referral URL construction)
 *   - ResellerServiceImpl     (store URL + referral URL construction)
 *   - ResellerStorefrontService
 *   - AffiliateRedirectController
 */
@Component
public class AppUrlProvider {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Returns the base URL with no trailing slash.
     * e.g. "https://yourdomain.com"
     */
    public String getBaseUrl() {
        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }

    /**
     * Build the full affiliate referral URL for a given code.
     * e.g. "https://yourdomain.com/a/A3KP9WZQ"
     */
    public String buildAffiliateUrl(String affiliateCode) {
        return getBaseUrl() + "/a/" + affiliateCode;
    }

    /**
     * Build the full reseller store URL for a given slug.
     * e.g. "https://yourdomain.com/store/kwame-data"
     */
    public String buildStoreUrl(String storeSlug) {
        return getBaseUrl() + "/store/" + storeSlug;
    }

    /**
     * Build the reseller referral link (for attracting sub-customers).
     * e.g. "https://yourdomain.com/ref/kwame-data"
     */
    public String buildResellerReferralUrl(String storeSlug) {
        return getBaseUrl() + "/ref/" + storeSlug;
    }
}