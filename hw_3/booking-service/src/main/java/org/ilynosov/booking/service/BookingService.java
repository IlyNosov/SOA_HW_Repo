package org.ilynosov.booking.service;

import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ilynosov.booking.dto.BookingDto;
import org.ilynosov.booking.entity.Booking;
import org.ilynosov.booking.repository.BookingRepository;
import org.ilynosov.flight.grpc.FlightInfo;
import org.ilynosov.flight.grpc.GetFlightResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.ilynosov.booking.client.FlightGrpcClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final FlightGrpcClient flightClient;

    public List<BookingDto.FlightResponse> searchFlights(String origin, String destination, String date) {
        var response = flightClient.searchFlights(origin, destination, date);
        return response.getFlightsList().stream()
                .map(this::toFlightDto)
                .toList();
    }

    public BookingDto.FlightResponse getFlightInfo(long id) {
        try {
            var response = flightClient.getFlight(id);
            return toFlightDto(response.getFlight());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Flight service error: " + e.getMessage());
        }
    }

    @Transactional
    public BookingDto.Response createBooking(BookingDto.CreateRequest request) {
        String bookingId = UUID.randomUUID().toString();

        // получаем информацию о рейсе
        GetFlightResponse flightResponse;
        try {
            flightResponse = flightClient.getFlight(request.getFlightId());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Flight service unavailable");
        }

        FlightInfo flight = flightResponse.getFlight();
        BigDecimal price = new BigDecimal(flight.getPrice());
        BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(request.getSeatCount()));

        // резервируем места через gRPC
        try {
            flightClient.reserveSeats(request.getFlightId(), request.getSeatCount(), bookingId);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.RESOURCE_EXHAUSTED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough seats available");
            }
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Flight service unavailable");
        }

        // сохраняем бронирование в БД
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(request.getUserId())
                .flightId(request.getFlightId())
                .passengerName(request.getPassengerName())
                .passengerEmail(request.getPassengerEmail())
                .seatCount(request.getSeatCount())
                .totalPrice(totalPrice)
                .status("CONFIRMED")
                .build();
        booking = bookingRepository.save(booking);

        log.info("Booking created: id={}, flight={}, seats={}, total={}",
                bookingId, request.getFlightId(), request.getSeatCount(), totalPrice);
        return toDto(booking);
    }

    public BookingDto.Response getBooking(String id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        return toDto(booking);
    }

    @Transactional
    public BookingDto.Response cancelBooking(String id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (!"CONFIRMED".equals(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is not in CONFIRMED status");
        }

        // возвращаем места через gRPC
        try {
            flightClient.releaseReservation(booking.getId());
        } catch (StatusRuntimeException e) {
            log.warn("Не удалось освободить резервацию для бронирования {}: {}", id, e.getMessage());
            // отменяем бронирование даже если освобождение мест не удалось
        }

        booking.setStatus("CANCELLED");
        bookingRepository.save(booking);

        log.info("Booking cancelled: id={}", id);
        return toDto(booking);
    }

    public List<BookingDto.Response> listBookings(String userId) {
        return bookingRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .toList();
    }

    // маппинг сущностей в DTO

    private BookingDto.Response toDto(Booking b) {
        return BookingDto.Response.builder()
                .id(b.getId())
                .userId(b.getUserId())
                .flightId(b.getFlightId())
                .passengerName(b.getPassengerName())
                .passengerEmail(b.getPassengerEmail())
                .seatCount(b.getSeatCount())
                .totalPrice(b.getTotalPrice().toPlainString())
                .status(b.getStatus())
                .createdAt(b.getCreatedAt() != null ? b.getCreatedAt().toString() : null)
                .updatedAt(b.getUpdatedAt() != null ? b.getUpdatedAt().toString() : null)
                .build();
    }

    private BookingDto.FlightResponse toFlightDto(FlightInfo f) {
        return BookingDto.FlightResponse.builder()
                .id(f.getId())
                .flightNumber(f.getFlightNumber())
                .origin(f.getOrigin())
                .destination(f.getDestination())
                .departureTime(timestampToString(f.getDepartureTime()))
                .arrivalTime(timestampToString(f.getArrivalTime()))
                .airline(f.getAirline())
                .totalSeats(f.getTotalSeats())
                .availableSeats(f.getAvailableSeats())
                .price(f.getPrice())
                .status(f.getStatus().name())
                .build();
    }

    private String timestampToString(com.google.protobuf.Timestamp ts) {
        return LocalDateTime.ofInstant(
                Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos()),
                ZoneOffset.UTC
        ).toString();
    }
}
