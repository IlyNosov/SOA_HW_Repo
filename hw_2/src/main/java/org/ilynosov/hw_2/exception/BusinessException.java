package org.ilynosov.hw_2.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;
    private final Object details;

    public BusinessException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.details = null;
    }

    public BusinessException(String errorCode, String message, HttpStatus status, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.details = details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Object getDetails() {
        return details;
    }
}