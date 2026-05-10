package org.ilynosov.hw6.consumer.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import lombok.RequiredArgsConstructor;
import org.ilynosov.hw6.consumer.config.CassandraProperties;
import org.ilynosov.hw6.consumer.model.WarehouseOrder;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepository {

    private final CqlSession cqlSession;
    private final CassandraProperties properties;

    public Optional<WarehouseOrder> findById(String orderId) {
        Row row = cqlSession.execute("""
            SELECT order_id, status, items_json, created_at, updated_at, last_event_timestamp
            FROM %s.orders_by_id
            WHERE order_id = ?
            """.formatted(properties.keyspace()), orderId).one();

        return Optional.ofNullable(row)
            .map(this::mapOrder);
    }

    public void save(WarehouseOrder order) {
        cqlSession.execute("""
            INSERT INTO %s.orders_by_id
            (order_id, status, items_json, created_at, updated_at, last_event_timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
            """.formatted(properties.keyspace()),
            order.orderId(),
            order.status(),
            order.itemsJson(),
            order.createdAt(),
            order.updatedAt(),
            order.lastEventTimestamp());
    }

    private WarehouseOrder mapOrder(Row row) {
        return new WarehouseOrder(
            row.getString("order_id"),
            row.getString("status"),
            row.getString("items_json"),
            getInstant(row, "created_at"),
            getInstant(row, "updated_at"),
            getInstant(row, "last_event_timestamp")
        );
    }

    private Instant getInstant(Row row, String column) {
        return row.isNull(column) ? null : row.getInstant(column);
    }
}
