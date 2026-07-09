package com.databundleHum.OnetBundleHub.repos;

import com.databundleHum.OnetBundleHub.entity.PlatformSettings;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, Long> {

    Optional<PlatformSettings> findByNetworkAndCapacityGb(
            PlatformSettings.Network network, BigDecimal capacityGb);

    Optional<PlatformSettings> findByNetworkAndCapacityGbAndActiveTrue(
            PlatformSettings.Network network, BigDecimal capacityGb);

    List<PlatformSettings> findByActiveTrue();
    /**
     * Used by ResellerServiceImpl to fetch the wholesale cost price for a given bundle.
     */
    @Query("SELECT s.resellerPriceGhc FROM PlatformSettings s " +
           "WHERE s.network = :network AND s.capacityGb = :gb AND s.active = true")
    Optional<BigDecimal> findResellerCostPrice(
            @Param("network") PlatformSettings.Network network,
            @Param("gb") BigDecimal capacityGb);
}