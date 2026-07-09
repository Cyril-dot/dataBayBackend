package com.databundleHum.OnetBundleHub.security;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}