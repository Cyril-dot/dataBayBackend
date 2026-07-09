package com.databundleHum.OnetBundleHub.dtos;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class TopUpRequest {
@NotNull(message = "Amount is required")
@DecimalMin(value = "1.00", message = "Minimum top-up is GHS 1.00")
@DecimalMax(value = "5000.00", message = "Maximum top-up is GHS 5,000")
private BigDecimal amount;
}
