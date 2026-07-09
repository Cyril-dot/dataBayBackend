package com.databundleHum.OnetBundleHub.security;

public class BundleNotFoundException extends RuntimeException {
    public BundleNotFoundException(String message) {
        super(message);
    }
}