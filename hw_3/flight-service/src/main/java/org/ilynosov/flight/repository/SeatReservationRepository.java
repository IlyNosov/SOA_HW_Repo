package org.ilynosov.flight.repository;

import org.ilynosov.flight.entity.SeatReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SeatReservationRepository extends JpaRepository<SeatReservation, Long> {

    Optional<SeatReservation> findByBookingIdAndStatus(String bookingId, String status);

    Optional<SeatReservation> findByBookingId(String bookingId);
}
