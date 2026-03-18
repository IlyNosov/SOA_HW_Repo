package org.ilynosov.booking.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

// реализация паттерна Circuit Breaker для gRPC вызовов к Flight Service
// состояния: CLOSED (нормальная работа), OPEN (блокировка), HALF_OPEN (пробный запрос)
@Component
@Slf4j
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile Instant openedAt = Instant.MIN;

    private final int failureThreshold;
    private final long waitDurationMs;

    public CircuitBreaker(
            @Value("${circuit-breaker.failure-threshold:5}") int failureThreshold,
            @Value("${circuit-breaker.wait-duration-seconds:15}") int waitDurationSeconds) {
        this.failureThreshold = failureThreshold;
        this.waitDurationMs = waitDurationSeconds * 1000L;
        log.info("CircuitBreaker создан: порог ошибок={}, таймаут={}с", failureThreshold, waitDurationSeconds);
    }

    // проверка состояния перед вызовом, бросает исключение если OPEN
    public void checkState() {
        State current = state.get();
        if (current == State.OPEN) {
            if (Instant.now().isAfter(openedAt.plusMillis(waitDurationMs))) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.warn("CircuitBreaker: OPEN -> HALF_OPEN (пробный запрос)");
                }
            } else {
                throw new CircuitBreakerOpenException("Circuit breaker is OPEN, flight service unavailable");
            }
        }
    }

    // вызывается при успешном ответе
    public void onSuccess() {
        if (state.get() == State.HALF_OPEN) {
            state.set(State.CLOSED);
            failureCount.set(0);
            log.warn("CircuitBreaker: HALF_OPEN -> CLOSED (пробный запрос успешен)");
        } else {
            failureCount.set(0);
        }
    }

    // вызывается при ошибке
    public void onFailure() {
        int failures = failureCount.incrementAndGet();
        if (state.get() == State.HALF_OPEN) {
            state.set(State.OPEN);
            openedAt = Instant.now();
            log.warn("CircuitBreaker: HALF_OPEN -> OPEN (пробный запрос провалился)");
        } else if (failures >= failureThreshold && state.compareAndSet(State.CLOSED, State.OPEN)) {
            openedAt = Instant.now();
            log.warn("CircuitBreaker: CLOSED -> OPEN (достигнут порог: {} ошибок)", failures);
        }
    }

    public State getState() {
        return state.get();
    }

    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) { super(message); }
    }
}