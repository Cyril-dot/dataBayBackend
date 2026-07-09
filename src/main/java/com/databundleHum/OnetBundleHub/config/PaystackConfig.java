package com.databundleHum.OnetBundleHub.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Paystack HTTP client configuration.
 *
 * Required environment variables:
 *   PAYSTACK_SECRET_KEY  — sk_live_... (never commit to Git)
 *   PAYSTACK_PUBLIC_KEY  — pk_live_... (also exposed to frontend via env)
 */
@Configuration
@Getter
public class PaystackConfig {

    @Value("${paystack.secret-key}")
    private String secretKey;

    @Value("${paystack.public-key}")
    private String publicKey;

    @Value("${paystack.base-url:https://api.paystack.co}")
    private String baseUrl;

    /**
     * Pre-configured WebClient for all Paystack REST calls.
     * Authorization header is set here so individual service calls don't repeat it.
     */
    @Bean(name = "paystackWebClient")
    public WebClient paystackWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + secretKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}