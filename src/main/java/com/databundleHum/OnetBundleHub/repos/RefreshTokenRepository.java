package com.databundleHum.OnetBundleHub.repos;

import com.databundleHum.OnetBundleHub.entity.RefreshToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId")
    void revokeAllByUserId(@Param("userId") UUID userId);

    /**
     * Called by TokenCleanupTask every hour.
     * Deletes rows that are both expired AND revoked — safe to hard-delete.
     * Active-but-expired tokens are left for one cycle so in-flight requests
     * still get a clean "token expired" error rather than a 404.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now AND rt.revoked = true")
    void deleteExpiredAndRevoked(@Param("now") Instant now);
}