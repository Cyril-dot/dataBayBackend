package com.databundleHum.OnetBundleHub.dtos;
 
import lombok.Builder;
import lombok.Data;
 
import java.time.LocalDateTime;
 
/**
 * Response item for GET /api/v1/reseller/sub-customers
 *
 * Privacy rule (architecture §10 decision 4): only masked info is exposed.
 * No email, phone, or full name — first name + last initial only.
 */
@Data
@Builder
public class SubCustomerResponse {
    /** Masked name: "Kwame A." */
    private String        maskedName;
    /** Total orders placed by this sub-customer (all time, any channel). */
    private long          orderCount;
    /** When they registered on the platform. */
    private LocalDateTime joinedAt;
}