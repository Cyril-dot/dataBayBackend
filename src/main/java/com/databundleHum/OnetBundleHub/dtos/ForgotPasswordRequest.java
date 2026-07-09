package com.databundleHum.OnetBundleHub.dtos;
 
import jakarta.validation.constraints.*;
import lombok.Data;
 
@Data
public class ForgotPasswordRequest {
 
    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;
}
 