package com.databundleHum.OnetBundleHub.repos;

import com.databundleHum.OnetBundleHub.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Page<WalletTransaction> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
}