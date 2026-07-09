package com.databundleHum.OnetBundleHub.dtos.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Returned by GET /api/admin/analytics?period=DAILY|WEEKLY|MONTHLY
 */
@Data
@Builder
public class AdminAnalyticsResponse {

    /** DAILY, WEEKLY, or MONTHLY */
    private String period;

    private List<DataPoint> revenueChart;
    private List<DataPoint> orderChart;

    @Data
    @Builder
    public static class DataPoint {

        private LocalDate date;
        private BigDecimal revenue;
        private long orderCount;
        private BigDecimal mtnRevenue;
        private BigDecimal telecelRevenue;
        private BigDecimal airteltigoRevenue;
    }
}