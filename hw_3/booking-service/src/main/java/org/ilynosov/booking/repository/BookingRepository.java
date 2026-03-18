package org.ilynosov.booking.repository;

import org.ilynosov.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, String> {

    List<Booking> findByUserId(String userId);
}
