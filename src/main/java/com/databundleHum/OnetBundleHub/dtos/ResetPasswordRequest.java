package com.databundleHum.OnetBundleHub.dtos;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ResetPasswordRequest {
    @NotBlank @Email private String email;
    @NotBlank private String previousPassword;
    @NotBlank @Size(min = 8) private String newPassword;
    @NotBlank private String confirmPassword;
}