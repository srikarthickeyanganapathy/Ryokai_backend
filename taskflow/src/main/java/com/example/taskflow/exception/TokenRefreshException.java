package com.example.taskflow.exception;

public class TokenRefreshException extends RuntimeException {

    public enum ErrorCode {
        INVALID_TOKEN, EXPIRED_TOKEN, REUSE_DETECTED, NOT_FOUND
    }

    private final ErrorCode errorCode;

    public TokenRefreshException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
