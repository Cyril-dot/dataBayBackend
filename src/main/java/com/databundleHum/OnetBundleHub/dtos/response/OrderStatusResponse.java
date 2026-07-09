package com.databundleHum.OnetBundleHub.dtos.response;

import com.databundleHum.OnetBundleHub.entity.Order.OrderStatus;
import lombok.Builder;
import lombok.Data;

/**
 * Returned by GET /api/orders/{id}/status — polled by frontend every 10s.
 */
@Data
@Builder
public class OrderStatusResponse {

    private Long orderId;
    private OrderStatus status;
    private String dbhReference;

    /** Human-readable status description */
    private String message;
}