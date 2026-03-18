package org.ilynosov.booking.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ilynosov.booking.dto.BookingDto;
import org.ilynosov.booking.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    // Flights

    @GetMapping("/flights")
    public List<BookingDto.FlightResponse> searchFlights(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam(required = false) String date) {
        return bookingService.searchFlights(origin, destination, date);
    }

    @GetMapping("/flights/{id}")
    public BookingDto.FlightResponse getFlight(@PathVariable long id) {
        return bookingService.getFlightInfo(id);
    }

    // Bookings

    @PostMapping("/bookings")
    public ResponseEntity<BookingDto.Response> createBooking(@Valid @RequestBody BookingDto.CreateRequest request) {
        BookingDto.Response response = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/bookings/{id}")
    public BookingDto.Response getBooking(@PathVariable String id) {
        return bookingService.getBooking(id);
    }

    @PostMapping("/bookings/{id}/cancel")
    public BookingDto.Response cancelBooking(@PathVariable String id) {
        return bookingService.cancelBooking(id);
    }

    @GetMapping("/bookings")
    public List<BookingDto.Response> listBookings(@RequestParam(name = "user_id") String userId) {
        return bookingService.listBookings(userId);
    }
}
