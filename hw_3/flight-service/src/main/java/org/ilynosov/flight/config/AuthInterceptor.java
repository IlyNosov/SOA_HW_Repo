package org.ilynosov.flight.config;

import io.grpc.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

// серверный interceptor для проверки API ключа в gRPC metadata
@Component
@GlobalServerInterceptor
public class AuthInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> API_KEY_HEADER =
            Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);

    @Value("${grpc.auth.api-key}")
    private String expectedApiKey;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String apiKey = headers.get(API_KEY_HEADER);

        if (apiKey == null || !apiKey.equals(expectedApiKey)) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid or missing API key"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        return next.startCall(call, headers);
    }
}