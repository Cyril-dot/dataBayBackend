// ─────────────────────────────────────────────────────────────────────────────
package com.databundleHum.OnetBundleHub.dtos;
 
import com.databundleHum.OnetBundleHub.entity.PlatformSettings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
 
import java.math.BigDecimal;
 
/**
 * Request body for POST /api/v1/store/{slug}/order/guest
 */
@Data
public class InitiateGuestStorefrontOrderRequest {
    @NotBlank
    private String phoneNumber;
    @NotNull
    private PlatformSettings.Network network;
    @NotNull
    private BigDecimal capacityGb;
}
 