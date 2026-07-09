package com.databundleHum.OnetBundleHub.config;

import com.databundleHum.OnetBundleHub.repos.PasswordResetTokenRepository;
import com.databundleHum.OnetBundleHub.repos.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
 
import java.time.Instant;
 
/**
 * Periodically purges expired/revoked tokens from the database.
 * Runs once per hour.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupTask {
 
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetRepository;
 
    @Scheduled(fixedRate = 3_600_000) // every hour
    @Transactional
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        refreshTokenRepository.deleteExpiredAndRevoked(now);
        passwordResetRepository.deleteExpiredAndUsed(now);
        log.debug("Token cleanup complete at {}", now);
    }
}