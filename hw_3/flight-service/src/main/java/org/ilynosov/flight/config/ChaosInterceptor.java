package org.ilynosov.flight.config;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

// тестовый interceptor, имитирует сбои для проверки retry на стороне клиента
// включается через переменную окружения GRPC_CHAOS_ENABLED=true
@Component
@GlobalServerInterceptor
@Slf4j
public class ChaosInterceptor implements ServerInterceptor {

    private final boolean enabled;
    private final AtomicInteger counter = new AtomicInteger(0);

    public ChaosInterceptor(@Value("${grpc.chaos.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        if (enabled) {
            int count = counter.incrementAndGet();
            // каждый 2й и 3й запрос из тройки будет падать
            if (count % 3 != 1) {
                log.warn("CHAOS: имитация сбоя UNAVAILABLE (запрос #{})", count);
                call.close(Status.UNAVAILABLE.withDescription("Chaos: simulated failure"), new Metadata());
                return new ServerCall.Listener<>() {};
            }
            log.info("CHAOS: пропускаем запрос #{}", count);
        }

        return next.startCall(call, headers);
    }
}