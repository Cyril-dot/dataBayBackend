package com.databundleHum.OnetBundleHub.repos;

import com.databundleHum.OnetBundleHub.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ResellerPricingRepository extends JpaRepository<ResellerPricing, Long> {

    Page<ResellerPricing> findByReseller(User reseller, Pageable pageable);

    Optional<ResellerPricing> findByResellerAndNetworkAndCapacityGb(
            User reseller, PlatformSettings.Network network, BigDecimal capacityGb);

    // ── NEW: storefront bundle list ───────────────────────────────────────────

    List<ResellerPricing> findByReseller(User reseller);


    /**
     * Fetch a reseller's pricing rows joined with only active platform bundles.
     * This ensures deactivated bundles are hidden from the storefront automatically.
     *
     * JPQL join: ResellerPricing rp JOIN PlatformSettings ps
     *            ON rp.network = ps.network AND rp.capacityGb = ps.capacityGb
     *            WHERE rp.reseller = :reseller AND ps.active = true
     */
    @Query("""
            SELECT rp FROM ResellerPricing rp
            JOIN PlatformSettings ps
              ON rp.network = ps.network AND rp.capacityGb = ps.capacityGb
            WHERE rp.reseller = :reseller
              AND ps.active = true
            ORDER BY rp.network, rp.capacityGb
            """)
    List<ResellerPricing> findByResellerWithActivePlatformSettings(
            @Param("reseller") User reseller);
}