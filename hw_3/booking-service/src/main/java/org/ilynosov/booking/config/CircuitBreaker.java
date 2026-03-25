package org.ilynosov.booking.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

// реализация паттерна Circuit Breaker для gRPC вызовов к Flight Service
// состояния: CLOSED (нормальная работа), OPEN (блокировка), HALF_OPEN (пробный запрос)
// решение об открытии принимается по скользящему окну последних slidingWindowSize вызовов
@Component
@Slf4j
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private volatile Instant openedAt = Instant.MIN;

    private final int failureThreshold;
    private final long waitDurationMs;

    // кольцевой буфер для скользящего окна: 1 = ошибка, 0 = успех
    private final int[] window;
    private int windowIndex = 0;
    private int windowCount = 0; // сколько записей уже заполнено (до slidingWindowSize)

    public CircuitBreaker(
            @Value("${circuit-breaker.failure-threshold:5}") int failureThreshold,
            @Value("${circuit-breaker.wait-duration-seconds:15}") int waitDurationSeconds,
            @Value("${circuit-breaker.sliding-window-size:10}") int slidingWindowSize) {
        this.failureThreshold = failureThreshold;
        this.waitDurationMs = waitDurationSeconds * 1000L;
        this.window = new int[slidingWindowSize];
        log.info("CircuitBreaker создан: порог ошибок={}, таймаут={}с, размер окна={}",
                failureThreshold, waitDurationSeconds, slidingWindowSize);
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
    public synchronized void onSuccess() {
        if (state.get() == State.HALF_OPEN) {
            state.set(State.CLOSED);
            resetWindow();
            log.warn("CircuitBreaker: HALF_OPEN -> CLOSED (пробный запрос успешен)");
        } else {
            recordResult(0);
        }
    }

    // вызывается при ошибке
    public synchronized void onFailure() {
        if (state.get() == State.HALF_OPEN) {
            state.set(State.OPEN);
            openedAt = Instant.now();
            log.warn("CircuitBreaker: HALF_OPEN -> OPEN (пробный запрос провалился)");
            return;
        }

        recordResult(1);
        int failures = countFailures();
        if (failures >= failureThreshold && state.compareAndSet(State.CLOSED, State.OPEN)) {
            openedAt = Instant.now();
            log.warn("CircuitBreaker: CLOSED -> OPEN (ошибок в окне: {}/{})", failures, windowCount);
        }
    }

    public State getState() {
        return state.get();
    }

    // записывает результат в кольцевой буфер
    private void recordResult(int value) {
        window[windowIndex] = value;
        windowIndex = (windowIndex + 1) % window.length;
        if (windowCount < window.length) {
            windowCount++;
        }
    }

    // считает количество ошибок в заполненной части окна
    private int countFailures() {
        int count = 0;
        for (int i = 0; i < windowCount; i++) {
            count += window[i];
        }
        return count;
    }

    // сбрасывает окно при переходе в CLOSED
    private void resetWindow() {
        windowIndex = 0;
        windowCount = 0;
        java.util.Arrays.fill(window, 0);
    }

    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) { super(message); }
    }
}