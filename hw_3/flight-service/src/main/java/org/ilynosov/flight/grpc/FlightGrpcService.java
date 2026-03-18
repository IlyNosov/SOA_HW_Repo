package org.ilynosov.flight.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ilynosov.flight.entity.Flight;
import org.ilynosov.flight.entity.SeatReservation;
import org.ilynosov.flight.service.FlightBusinessService;
import org.ilynosov.flight.service.FlightBusinessService.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlightGrpcService extends FlightServiceGrpc.FlightServiceImplBase {

    private final FlightBusinessService flightBusinessService;

    @Override
    public void searchFlights(SearchFlightsRequest request, StreamObserver<SearchFlightsResponse> responseObserver) {
        log.debug("SearchFlights: origin={}, destination={}, date={}",
                request.getOrigin(), request.getDestination(), request.getDate());

        if (request.getOrigin().isBlank() || request.getDestination().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("origin and destination are required")
                    .asRuntimeException());
            return;
        }

        List<Flight> flights = flightBusinessService.searchFlights(
                request.getOrigin(), request.getDestination(),
                request.getDate().isBlank() ? null : request.getDate());

        SearchFlightsResponse.Builder responseBuilder = SearchFlightsResponse.newBuilder();
        for (Flight f : flights) {
            responseBuilder.addFlights(toProto(f));
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getFlight(GetFlightRequest request, StreamObserver<GetFlightResponse> responseObserver) {
        log.debug("GetFlight: id={}", request.getId());

        flightBusinessService.getFlight(request.getId()).ifPresentOrElse(
                flight -> {
                    responseObserver.onNext(GetFlightResponse.newBuilder()
                            .setFlight(toProto(flight))
                            .build());
                    responseObserver.onCompleted();
                },
                () -> responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Flight not found: " + request.getId())
                        .asRuntimeException())
        );
    }

    @Override
    public void reserveSeats(ReserveSeatsRequest request, StreamObserver<ReserveSeatsResponse> responseObserver) {
        log.debug("ReserveSeats: flight_id={}, seat_count={}, booking_id={}",
                request.getFlightId(), request.getSeatCount(), request.getBookingId());

        if (request.getBookingId().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("booking_id is required")
                    .asRuntimeException());
            return;
        }
        if (request.getSeatCount() <= 0) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("seat_count must be positive")
                    .asRuntimeException());
            return;
        }

        try {
            SeatReservation reservation = flightBusinessService.reserveSeats(
                    request.getFlightId(), request.getSeatCount(), request.getBookingId());

            responseObserver.onNext(ReserveSeatsResponse.newBuilder()
                    .setReservationId(reservation.getId())
                    .setStatus(ReservationStatus.RESERVATION_STATUS_ACTIVE)
                    .build());
            responseObserver.onCompleted();

        } catch (FlightNotFoundException e) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage()).asRuntimeException());
        } catch (InsufficientSeatsException e) {
            responseObserver.onError(Status.RESOURCE_EXHAUSTED
                    .withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void releaseReservation(ReleaseReservationRequest request,
                                   StreamObserver<ReleaseReservationResponse> responseObserver) {
        log.debug("ReleaseReservation: booking_id={}", request.getBookingId());

        if (request.getBookingId().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("booking_id is required")
                    .asRuntimeException());
            return;
        }

        try {
            boolean released = flightBusinessService.releaseReservation(request.getBookingId());
            responseObserver.onNext(ReleaseReservationResponse.newBuilder()
                    .setReleased(released)
                    .build());
            responseObserver.onCompleted();

        } catch (ReservationNotFoundException e) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage()).asRuntimeException());
        }
    }

    // маппинг сущностей в protobuf

    private FlightInfo toProto(Flight f) {
        return FlightInfo.newBuilder()
                .setId(f.getId())
                .setFlightNumber(f.getFlightNumber())
                .setOrigin(f.getOrigin())
                .setDestination(f.getDestination())
                .setDepartureTime(toTimestamp(f.getDepartureTime()))
                .setArrivalTime(toTimestamp(f.getArrivalTime()))
                .setAirline(f.getAirline())
                .setTotalSeats(f.getTotalSeats())
                .setAvailableSeats(f.getAvailableSeats())
                .setPrice(f.getPrice().toPlainString())
                .setStatus(mapStatus(f.getStatus()))
                .build();
    }

    private Timestamp toTimestamp(LocalDateTime ldt) {
        var instant = ldt.toInstant(ZoneOffset.UTC);
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    private FlightStatus mapStatus(String status) {
        return switch (status) {
            case "SCHEDULED" -> FlightStatus.FLIGHT_STATUS_SCHEDULED;
            case "DEPARTED"  -> FlightStatus.FLIGHT_STATUS_DEPARTED;
            case "CANCELLED" -> FlightStatus.FLIGHT_STATUS_CANCELLED;
            case "COMPLETED" -> FlightStatus.FLIGHT_STATUS_COMPLETED;
            default          -> FlightStatus.FLIGHT_STATUS_UNSPECIFIED;
        };
    }
}
