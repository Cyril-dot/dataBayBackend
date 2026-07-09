package com.databundleHum.OnetBundleHub.dtos;
 
import lombok.Builder;
import lombok.Data;
 
import java.util.UUID;
 
/**
 * Response for GET/PUT /api/v1/auth/profile
 *
 * Changes: added isAffiliate and affiliateCode so the profile page can render
 * the affiliate programme card and referral link without an extra round-trip.
 */
@Data
@Builder
public class ProfileResponse {
    private UUID    userId;
    private String  fullName;
    private String  email;
    private String  phone;
    private String  role;
    private double  walletBalance;
    private boolean active;
    private boolean isAffiliate;
    /**
     * The user's affiliate code (null if they have never activated the programme).
     * Present even when isAffiliate=false (code is retained after deactivation).
     */
    private String  affiliateCode;
    private String  createdAt;
}