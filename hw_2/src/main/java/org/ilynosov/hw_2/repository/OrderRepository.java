package org.ilynosov.hw_2.repository;

import org.ilynosov.hw_2.entity.Order;
import org.ilynosov.hw_2.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
            UUID userId,
            Iterable<OrderStatus> statuses
    );
}