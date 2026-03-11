package org.ilynosov.hw_2.exception;

import lombok.extern.slf4j.Slf4j;
import org.ilynosov.hw_2.model.ErrorResponse;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error_code", ex.getErrorCode());
        body.put("message", ex.getMessage());

        if (ex.getDetails() != null) {
            body.put("details", ex.getDetails());
        }

        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(err ->
                errors.put(err.getField(), err.getDefaultMessage())
        );

        ErrorResponse response = new ErrorResponse();
        response.setErrorCode("VALIDATION_ERROR");
        response.setMessage("Validation failed");
        response.setDetails(JsonNullable.of(errors));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {

        log.error("Unexpected error", ex);

        ErrorResponse response = new ErrorResponse();
        response.setErrorCode("INTERNAL_ERROR");
        response.setMessage("Unexpected server error");

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(ProductNotFoundException ex) {
        ErrorResponse body = new ErrorResponse()
                .errorCode("PRODUCT_NOT_FOUND")
                .message(ex.getMessage() != null ? ex.getMessage() : "Product not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(
            MissingRequestHeaderException ex
    ) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error_code", "VALIDATION_ERROR");
        body.put("message", "Required header is missing: " + ex.getHeaderName());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex
    ) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error_code", "VALIDATION_ERROR");

        body.put(
                "message",
                "Invalid value for parameter '" + ex.getName() + "'"
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(body);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaType(
            HttpMediaTypeNotSupportedException ex
    ) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error_code", "VALIDATION_ERROR");
        body.put("message", "Content-Type not supported");

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(body);
    }
}