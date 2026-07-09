package com.databundleHum.OnetBundleHub.security;

public abstract class AppException extends RuntimeException {
    protected AppException(String message) { super(message); }
}