package org.ilynosov.hw6.consumer.exception;

public class WarehouseEventBusinessException extends RuntimeException {

    public WarehouseEventBusinessException(String message) {
        super(message);
    }

    public WarehouseEventBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
