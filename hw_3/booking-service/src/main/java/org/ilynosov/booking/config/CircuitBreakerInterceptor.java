package org.ilynosov.booking.config;

import io.grpc.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// circuit breaker как gRPC client interceptor
// перехватывает все вызовы к Flight Service на уровне транспорта
@Component
@Slf4j
@RequiredArgsConstructor
public class CircuitBreakerInterceptor implements ClientInterceptor {

    private final CircuitBreaker circuitBreaker;

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        // проверяем состояние circuit breaker до вызова
        try {
            circuitBreaker.checkState();
        } catch (CircuitBreaker.CircuitBreakerOpenException e) {
            // сразу возвращаем ошибку UNAVAILABLE без реального вызова
            return new FailedClientCall<>(e.getMessage());
        }

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        if (status.isOk()) {
                            circuitBreaker.onSuccess();
                        } else if (isServerError(status.getCode())) {
                            circuitBreaker.onFailure();
                        }
                        super.onClose(status, trailers);
                    }
                }, headers);
            }
        };
    }

    private boolean isServerError(Status.Code code) {
        return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED;
    }

    // заглушка для немедленного отказа когда circuit breaker открыт
    private static class FailedClientCall<ReqT, RespT> extends ClientCall<ReqT, RespT> {
        private final String message;

        FailedClientCall(String message) {
            this.message = message;
        }

        @Override
        public void start(Listener<RespT> responseListener, Metadata headers) {
            responseListener.onClose(
                    Status.UNAVAILABLE.withDescription(message),
                    new Metadata());
        }

        @Override public void request(int numMessages) {}
        @Override public void cancel(String message, Throwable cause) {}
        @Override public void halfClose() {}
        @Override public void sendMessage(ReqT message) {}
    }
}