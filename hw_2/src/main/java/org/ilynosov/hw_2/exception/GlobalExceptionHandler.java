package org.ilynosov.hw_2.exception;

import lombok.extern.slf4j.Slf4j;
import org.ilynosov.hw_2.model.ErrorResponse;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {

        ErrorResponse response = new ErrorResponse();
        response.setErrorCode(ex.getErrorCode());
        response.setMessage(ex.getMessage());

        if (ex.getDetails() != null) {
            response.setDetails(JsonNullable.of(ex.getDetails()));
        }

        return ResponseEntity
                .status(ex.getStatus())
                .body(response);
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
}