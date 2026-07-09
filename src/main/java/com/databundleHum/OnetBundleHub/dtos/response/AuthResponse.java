package com.databundleHum.OnetBundleHub.dtos.response;

import com.databundleHum.OnetBundleHub.entity.User.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {

    private Long userId;
    private String email;
    private String fullName;
    private Role role;

    /** Bearer token — 15 min expiry */
    private String accessToken;

    // Refresh token is set as HttpOnly cookie by the controller
}