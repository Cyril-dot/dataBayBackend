package com.databundleHum.OnetBundleHub.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResellerApplicationRequest {

    @NotBlank(message = "Application note is required")
    private String applicationNote;
}