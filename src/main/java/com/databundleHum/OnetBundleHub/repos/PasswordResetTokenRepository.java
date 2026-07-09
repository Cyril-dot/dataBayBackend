package com.databundleHum.OnetBundleHub.repos;

import com.databundleHum.OnetBundleHub.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByOtpAndUsedFalse(String otp);

    // Find latest unused OTP for user (to prevent duplicate requests flooding DB)
    @Query("SELECT p FROM PasswordResetToken p WHERE p.user.email = :email " +
           "AND p.used = false AND p.expiresAt > :now ORDER BY p.createdAt DESC")
    Optional<PasswordResetToken> findActiveOtpByEmail(
            @Param("email") String email,
            @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("UPDATE PasswordResetToken p SET p.used = true WHERE p.user.email = :email")
    void invalidateAllForUser(@Param("email") String email);

    @Transactional
    @Modifying
    @Query("DELETE FROM PasswordResetToken p WHERE p.expiresAt < :now OR p.used = true")
    void deleteExpiredAndUsed(@Param("now") Instant now);
}