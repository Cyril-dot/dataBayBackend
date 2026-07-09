package com.databundleHum.OnetBundleHub.dtos;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ChangePasswordRequest {
    @NotBlank private String currentPassword;
    @NotBlank @Size(min = 8) private String newPassword;
    @NotBlank private String confirmPassword;
}