package org.ilynosov.booking.client;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.ilynosov.flight.grpc.*;
import org.springframework.stereotype.Component;

// gRPC клиент с retry и exponential backoff
// circuit breaker работает как interceptor на уровне канала
@Component
@Slf4j
public class FlightGrpcClient {

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 100;

    private final FlightServiceGrpc.FlightServiceBlockingStub stub;

    public FlightGrpcClient(FlightServiceGrpc.FlightServiceBlockingStub stub) {
        this.stub = stub;
    }

    public SearchFlightsResponse searchFlights(String origin, String destination, String date) {
        log.debug("gRPC searchFlights: origin={}, dest={}, date={}", origin, destination, date);
        SearchFlightsRequest.Builder request = SearchFlightsRequest.newBuilder()
                .setOrigin(origin)
                .setDestination(destination);
        if (date != null) {
            request.setDate(date);
        }
        return callWithRetry(() -> stub.searchFlights(request.build()), "SearchFlights");
    }

    public GetFlightResponse getFlight(long id) {
        log.debug("gRPC getFlight: id={}", id);
        return callWithRetry(() -> stub.getFlight(
                GetFlightRequest.newBuilder().setId(id).build()), "GetFlight");
    }

    public ReserveSeatsResponse reserveSeats(long flightId, int seatCount, String bookingId) {
        log.debug("gRPC reserveSeats: flight={}, seats={}, booking={}", flightId, seatCount, bookingId);
        return callWithRetry(() -> stub.reserveSeats(
                ReserveSeatsRequest.newBuilder()
                        .setFlightId(flightId)
                        .setSeatCount(seatCount)
                        .setBookingId(bookingId)
                        .build()), "ReserveSeats");
    }

    public ReleaseReservationResponse releaseReservation(String bookingId) {
        log.debug("gRPC releaseReservation: booking={}", bookingId);
        return callWithRetry(() -> stub.releaseReservation(
                ReleaseReservationRequest.newBuilder()
                        .setBookingId(bookingId)
                        .build()), "ReleaseReservation");
    }

    // retry с exponential backoff для UNAVAILABLE и DEADLINE_EXCEEDED
    private <T> T callWithRetry(java.util.function.Supplier<T> call, String methodName) {
        int attempt = 0;
        while (true) {
            try {
                return call.get();
            } catch (StatusRuntimeException e) {
                if (isRetryable(e.getStatus().getCode()) && attempt < MAX_RETRIES) {
                    attempt++;
                    long backoff = INITIAL_BACKOFF_MS * (1L << (attempt - 1));
                    log.warn("gRPC retry попытка {}/{} для {} через {}ms (причина: {})",
                            attempt, MAX_RETRIES, methodName, backoff, e.getStatus().getCode());
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                } else {
                    throw e;
                }
            }
        }
    }

    private boolean isRetryable(Status.Code code) {
        return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED;
    }
}