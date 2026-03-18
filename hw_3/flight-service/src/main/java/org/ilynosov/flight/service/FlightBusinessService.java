package org.ilynosov.flight.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ilynosov.flight.entity.Flight;
import org.ilynosov.flight.entity.SeatReservation;
import org.ilynosov.flight.repository.FlightRepository;
import org.ilynosov.flight.repository.SeatReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlightBusinessService {

    private final FlightRepository flightRepository;
    private final SeatReservationRepository reservationRepository;
    private final FlightCacheService cache;

    @Transactional(readOnly = true)
    public List<Flight> searchFlights(String origin, String destination, String date) {
        // пробуем взять из кеша
        String cacheKey = "search:" + origin + ":" + destination + ":" + (date != null ? date : "all");
        Optional<Flight[]> cached = cache.get(cacheKey, Flight[].class);
        if (cached.isPresent()) {
            return List.of(cached.get());
        }

        LocalDateTime dateFrom = null;
        LocalDateTime dateTo = null;
        if (date != null && !date.isBlank()) {
            LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
            dateFrom = localDate.atStartOfDay();
            dateTo = localDate.plusDays(1).atStartOfDay();
        }

        List<Flight> flights = flightRepository.searchFlights(origin, destination, dateFrom, dateTo);

        // кладем в кеш
        cache.put(cacheKey, flights.toArray(new Flight[0]));
        return flights;
    }

    @Transactional(readOnly = true)
    public Optional<Flight> getFlight(long id) {
        // пробуем взять из кеша
        String cacheKey = "flight:" + id;
        Optional<Flight> cached = cache.get(cacheKey, Flight.class);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<Flight> flight = flightRepository.findById(id);
        flight.ifPresent(f -> cache.put(cacheKey, f));
        return flight;
    }

    // атомарное резервирование мест: SELECT FOR UPDATE + уменьшение available_seats + создание резервации
    // идемпотентность по booking_id
    @Transactional
    public SeatReservation reserveSeats(long flightId, int seatCount, String bookingId) {
        // проверка идемпотентности
        Optional<SeatReservation> existing = reservationRepository.findByBookingId(bookingId);
        if (existing.isPresent()) {
            log.info("Идемпотентный запрос: резервация уже существует для booking_id={}", bookingId);
            return existing.get();
        }

        // блокируем строку рейса через SELECT FOR UPDATE
        Flight flight = flightRepository.findByIdForUpdate(flightId)
                .orElseThrow(() -> new FlightNotFoundException("Flight not found: " + flightId));

        if (flight.getAvailableSeats() < seatCount) {
            throw new InsufficientSeatsException(
                    "Not enough seats: requested=" + seatCount + ", available=" + flight.getAvailableSeats());
        }

        // уменьшаем количество доступных мест
        flight.setAvailableSeats(flight.getAvailableSeats() - seatCount);
        flightRepository.save(flight);

        // создаем резервацию
        SeatReservation reservation = SeatReservation.builder()
                .flightId(flightId)
                .bookingId(bookingId)
                .seatCount(seatCount)
                .status("ACTIVE")
                .build();
        reservationRepository.save(reservation);

        // инвалидируем кеш после мутации
        cache.evict("flight:" + flightId);
        cache.evictByPrefix("search:");

        log.info("Зарезервировано {} мест на рейсе {} для бронирования {}", seatCount, flightId, bookingId);
        return reservation;
    }

    // отмена резервации: возврат мест + статус RELEASED в одной транзакции
    @Transactional
    public boolean releaseReservation(String bookingId) {
        SeatReservation reservation = reservationRepository
                .findByBookingIdAndStatus(bookingId, "ACTIVE")
                .orElseThrow(() -> new ReservationNotFoundException(
                        "No active reservation for booking_id=" + bookingId));

        // блокируем строку рейса
        Flight flight = flightRepository.findByIdForUpdate(reservation.getFlightId())
                .orElseThrow(() -> new FlightNotFoundException("Flight not found: " + reservation.getFlightId()));

        // возвращаем места
        flight.setAvailableSeats(flight.getAvailableSeats() + reservation.getSeatCount());
        flightRepository.save(flight);

        // помечаем резервацию как отмененную
        reservation.setStatus("RELEASED");
        reservationRepository.save(reservation);

        // инвалидируем кеш после мутации
        cache.evict("flight:" + reservation.getFlightId());
        cache.evictByPrefix("search:");

        log.info("Отменена резервация для бронирования {} ({} мест возвращено на рейс {})",
                bookingId, reservation.getSeatCount(), reservation.getFlightId());
        return true;
    }

    // классы исключений
    public static class FlightNotFoundException extends RuntimeException {
        public FlightNotFoundException(String message) { super(message); }
    }

    public static class InsufficientSeatsException extends RuntimeException {
        public InsufficientSeatsException(String message) { super(message); }
    }

    public static class ReservationNotFoundException extends RuntimeException {
        public ReservationNotFoundException(String message) { super(message); }
    }
}