package org.ilynosov.booking.dto;

import jakarta.validation.constraints.*;
import lombok.*;

public class BookingDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank private String userId;
        @NotNull  private Long flightId;
        @NotBlank @Size(max = 255) private String passengerName;
        @NotBlank @Email @Size(max = 255) private String passengerEmail;
        @NotNull @Min(1) private Integer seatCount;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private String id;
        private String userId;
        private Long flightId;
        private String passengerName;
        private String passengerEmail;
        private Integer seatCount;
        private String totalPrice;
        private String status;
        private String createdAt;
        private String updatedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FlightResponse {
        private Long id;
        private String flightNumber;
        private String origin;
        private String destination;
        private String departureTime;
        private String arrivalTime;
        private String airline;
        private Integer totalSeats;
        private Integer availableSeats;
        private String price;
        private String status;
    }
}
