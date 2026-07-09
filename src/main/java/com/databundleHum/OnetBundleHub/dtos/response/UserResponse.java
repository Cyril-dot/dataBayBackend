package com.databundleHum.OnetBundleHub.dtos.response;

import com.databundleHum.OnetBundleHub.entity.User.Role;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
public class UserResponse {

    private Long id;
    private String email;
    private String phone;
    private String fullName;
    private Role role;
    private BigDecimal walletBalance;
    private boolean isActive;
    private OffsetDateTime createdAt;
}