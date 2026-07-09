package com.databundleHum.OnetBundleHub.repos;

import com.databundleHum.OnetBundleHub.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface PayoutRepository extends JpaRepository<Payout, Long> {

    Page<Payout> findByResellerOrderByCreatedAtDesc(User reseller, Pageable pageable);

    Page<Payout> findByStatusOrderByCreatedAtAsc(Payout.PayoutStatus status, Pageable pageable);

    long countByStatus(Payout.PayoutStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payout p WHERE p.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") Payout.PayoutStatus status);
}