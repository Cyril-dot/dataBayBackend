package com.databundleHum.OnetBundleHub.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidCredentialsException extends AppException {
    public InvalidCredentialsException(String message) { super(message); }
}