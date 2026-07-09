package com.databundleHum.OnetBundleHub.dtos;
 
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
 
/**
 * Request body for POST /api/v1/auth/register
 *
 * Changes from previous version:
 *   - Added affiliateCode  (optional — from ref_affiliate cookie, sent by frontend)
 *   - Added resellerSlug   (optional — from ref_reseller_id cookie, sent by frontend)
 *
 * The frontend reads both cookies at registration time and includes them
 * in the request body. The backend resolves them to User FKs in AuthService.
 *
 * Neither field is required — registration proceeds normally if both are absent.
 */
@Data
public class RegisterRequest {
 
    @NotBlank
    private String fullName;
 
    @NotBlank
    @Email
    private String email;
 
    @NotBlank
    private String phone;
 
    @NotBlank
    @Size(min = 8)
    private String password;
 
    /**
     * Value of the ref_affiliate cookie, e.g. "A3KP9WZQ".
     * Null if the user did not arrive via an affiliate referral link.
     */
    private String affiliateCode;
 
    /**
     * Value of the ref_reseller_id cookie, e.g. "kwame-data".
     * Null if the user did not arrive via a reseller referral link.
     */
    private String resellerSlug;
}