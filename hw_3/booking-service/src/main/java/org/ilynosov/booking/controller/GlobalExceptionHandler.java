package org.ilynosov.booking.controller;

import io.grpc.StatusRuntimeException;
import org.ilynosov.booking.config.CircuitBreaker;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

// глобальная обработка ошибок
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CircuitBreaker.CircuitBreakerOpenException.class)
    public ResponseEntity<Map<String, String>> handleCircuitBreakerOpen(
            CircuitBreaker.CircuitBreakerOpenException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "SERVICE_UNAVAILABLE",
                "message", "Flight service is temporarily unavailable, please try again later"
        ));
    }

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<Map<String, String>> handleGrpcError(StatusRuntimeException e) {
        HttpStatus status = switch (e.getStatus().getCode()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RESOURCE_EXHAUSTED -> HttpStatus.CONFLICT;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        return ResponseEntity.status(status).body(Map.of(
                "error", e.getStatus().getCode().name(),
                "message", e.getStatus().getDescription() != null
                        ? e.getStatus().getDescription() : "Unknown error"
        ));
    }
}