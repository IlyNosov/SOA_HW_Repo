package org.ilynosov.booking.config;

import org.ilynosov.flight.grpc.FlightServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientConfig {

    @Bean
    FlightServiceGrpc.FlightServiceBlockingStub flightServiceBlockingStub(
            GrpcChannelFactory channels,
            AuthClientInterceptor authInterceptor,
            CircuitBreakerInterceptor circuitBreakerInterceptor) {
        return FlightServiceGrpc.newBlockingStub(
                        channels.createChannel("flight-service"))
                .withInterceptors(authInterceptor, circuitBreakerInterceptor);
    }
}