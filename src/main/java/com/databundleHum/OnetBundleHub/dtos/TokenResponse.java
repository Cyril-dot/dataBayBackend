// ─────────────────────────────────────────────────────────────────────────────
package com.databundleHum.OnetBundleHub.dtos;
 
import lombok.Builder;
import lombok.Data;
 
import java.util.UUID;
 
/**
 * Response for login, register, and token refresh.
 *
 * Changes: added isAffiliate so the frontend can immediately show or hide
 * the affiliate dashboard menu item without a separate profile call.
 */
@Data
@Builder
public class TokenResponse {
    private String  accessToken;
    private String  tokenType;
    private long    expiresIn;
    private String  role;
    private UUID    userId;
    private String  fullName;
    private double  walletBalance;
    /** Whether the user has activated the affiliate programme. */
    private boolean isAffiliate;
}
 