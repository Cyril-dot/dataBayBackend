// ─────────────────────────────────────────────────────────────────────────────
package com.databundleHum.OnetBundleHub.dtos;
 
import lombok.Data;
 
/**
 * Request body for admin approve/reject reseller endpoints.
 *
 * Changes:
 *  - Added slugOverride: if supplied, the admin can set a custom slug
 *    instead of the auto-generated one. Must be unique.
 */
@Data
public class AdminResellerActionRequest {
    /** Optional note (shown to the reseller in their notification email). */
    private String note;
    /**
     * Optional slug override for the approve flow only.
     * If null or blank, the slug is auto-generated from the reseller's full name.
     * Ignored on reject.
     */
    private String slugOverride;
}