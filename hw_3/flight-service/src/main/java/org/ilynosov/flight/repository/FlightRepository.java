package org.ilynosov.flight.repository;

import jakarta.persistence.LockModeType;
import org.ilynosov.flight.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long> {

    // Поиск по маршруту и дате
    @Query(value = "SELECT * FROM flights f WHERE f.origin = :origin AND f.destination = :destination " +
            "AND f.status = 'SCHEDULED' " +
            "AND (CAST(:dateFrom AS TIMESTAMP) IS NULL OR f.departure_time >= :dateFrom) " +
            "AND (CAST(:dateTo AS TIMESTAMP) IS NULL OR f.departure_time < :dateTo)",
            nativeQuery = true)
    List<Flight> searchFlights(@Param("origin") String origin,
                               @Param("destination") String destination,
                               @Param("dateFrom") LocalDateTime dateFrom,
                               @Param("dateTo") LocalDateTime dateTo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Flight f WHERE f.id = :id")
    Optional<Flight> findByIdForUpdate(Long id);
}
