package com.databundleHum.OnetBundleHub.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidTokenException extends AppException {
    public InvalidTokenException(String message) { super(message); }
}