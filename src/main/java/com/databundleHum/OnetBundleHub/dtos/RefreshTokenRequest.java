package com.databundleHum.OnetBundleHub.dtos;
 
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
 
@Data
public class RefreshTokenRequest {
 
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}