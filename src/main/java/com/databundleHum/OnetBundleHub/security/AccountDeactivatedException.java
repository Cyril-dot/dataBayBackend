package com.databundleHum.OnetBundleHub.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccountDeactivatedException extends AppException {
    public AccountDeactivatedException(String message) { super(message); }
}