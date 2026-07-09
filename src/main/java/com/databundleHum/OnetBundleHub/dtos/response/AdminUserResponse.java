package com.databundleHum.OnetBundleHub.dtos.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** Returned by GET /api/admin/users and GET /api/admin/users/{id}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserResponse {
    private UUID id;
    private String     fullName;
    private String     email;
    private String     phone;
    private String     role;           // User.Role.name()
    private BigDecimal walletBalance;
    private boolean    active;
    private LocalDateTime createdAt;
}