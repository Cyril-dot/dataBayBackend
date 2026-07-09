package com.databundleHum.OnetBundleHub.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class PriceBelowCostException extends RuntimeException {
    public PriceBelowCostException(String message) { super(message); }
}
