package com.databundleHum.OnetBundleHub.dtos;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateProfileRequest {
    private String fullName;
    private String phone;
}