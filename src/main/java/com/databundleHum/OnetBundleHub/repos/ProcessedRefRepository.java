package com.databundleHum.OnetBundleHub.repos;

import com.databundleHum.OnetBundleHub.entity.ProcessedRef;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedRefRepository extends JpaRepository<ProcessedRef, Long> {

    boolean existsByReference(String reference);
}