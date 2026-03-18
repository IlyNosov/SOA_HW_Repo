package org.ilynosov.booking.config;

import io.grpc.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// клиентский interceptor, добавляет API ключ в metadata каждого gRPC вызова
@Component
public class AuthClientInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> API_KEY_HEADER =
            Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);

    @Value("${grpc.auth.api-key}")
    private String apiKey;

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(API_KEY_HEADER, apiKey);
                super.start(responseListener, headers);
            }
        };
    }
}