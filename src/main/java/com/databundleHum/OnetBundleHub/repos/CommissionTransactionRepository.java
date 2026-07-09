package com.databundleHum.OnetBundleHub.repos;

import com.databundleHum.OnetBundleHub.entity.CommissionTransaction;
import com.databundleHum.OnetBundleHub.entity.Order;
import com.databundleHum.OnetBundleHub.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface CommissionTransactionRepository extends JpaRepository<CommissionTransaction, Long> {

    /**
     * Find the commission record for a specific order.
     * Used by reverseCommission() to locate the row before flagging it reversed.
     */
    Optional<CommissionTransaction> findByOrder(Order order);

    /**
     * Paginated commission history for a given affiliate.
     * Ordered newest-first for the dashboard table.
     */
    Page<CommissionTransaction> findByAffiliateUserOrderByCreatedAtDesc(
            User affiliateUser, Pageable pageable);

    /**
     * Total commission earned by an affiliate (all time, including reversed).
     * The dashboard shows gross earned; reversals are itemised separately.
     * Use sumNonReversed for the "net" figure if needed.
     */
    @Query("""
            SELECT COALESCE(SUM(ct.commissionGhc), 0)
            FROM CommissionTransaction ct
            WHERE ct.affiliateUser.id = :affiliateId
              AND ct.reversed = false
            """)
    BigDecimal sumEarnedByAffiliate(@Param("affiliateId") UUID affiliateId);

    /**
     * Commission earned this calendar month (non-reversed).
     */
    @Query("""
            SELECT COALESCE(SUM(ct.commissionGhc), 0)
            FROM CommissionTransaction ct
            WHERE ct.affiliateUser.id = :affiliateId
              AND ct.reversed = false
              AND ct.createdAt >= :monthStart
            """)
    BigDecimal sumEarnedByAffiliateThisMonth(
            @Param("affiliateId") UUID affiliateId,
            @Param("monthStart") LocalDateTime monthStart);

    /**
     * Count of distinct referred users who have placed at least one qualifying order
     * (i.e. appeared in a CommissionTransaction for this affiliate).
     */
    @Query("""
            SELECT COUNT(DISTINCT ct.referredUser.id)
            FROM CommissionTransaction ct
            WHERE ct.affiliateUser.id = :affiliateId
            """)
    long countDistinctReferredUsersByAffiliate(@Param("affiliateId") UUID affiliateId);
}