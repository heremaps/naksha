package com.here.naksha.cli.copy.service;

public final class CopyServiceException extends Exception {
    public CopyServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public CopyServiceException(String message) {
        super(message);
    }
}
