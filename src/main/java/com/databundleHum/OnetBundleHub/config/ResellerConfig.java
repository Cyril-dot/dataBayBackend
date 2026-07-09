package com.databundleHum.OnetBundleHub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * All tunable reseller business rules live here.
 * Add to application.yml:
 *
 * <pre>
 * app:
 *   reseller:
 *     registration-fee-ghc: 20.00
 *     min-payout-ghc: 5.00
 * </pre>
 */
@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "app.reseller")
public class ResellerConfig {

    /**
     * One-time registration fee (GHS). Default: 20.00.
     * Change in application.yml — do NOT hard-code in service layer.
     */
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal registrationFeeGhc = new BigDecimal("20.00");

    /**
     * Minimum amount a reseller can request as a payout (GHS). Default: 5.00.
     */
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal minPayoutGhc = new BigDecimal("5.00");

    /**
     * Whether a reseller's selling price must strictly exceed the cost price
     * (true = must be strictly greater; false = must be >= cost price).
     */
    private boolean enforceStrictMarkup = false;
}