package org.ilynosov.hw6.consumer.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import lombok.RequiredArgsConstructor;
import org.ilynosov.hw6.consumer.config.CassandraProperties;
import org.ilynosov.hw6.consumer.model.WarehouseEventHistoryItem;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WarehouseEventHistoryRepository {

    private final CqlSession cqlSession;
    private final CassandraProperties properties;

    public void save(WarehouseEventHistoryItem item) {
        cqlSession.execute("""
            INSERT INTO %s.warehouse_events_history
            (product_id, event_timestamp, event_id, event_type, zone_id, payload, source_partition, source_offset)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.formatted(properties.keyspace()),
            item.productId(),
            item.eventTimestamp(),
            item.eventId(),
            item.eventType(),
            item.zoneId(),
            item.payload(),
            item.sourcePartition(),
            item.sourceOffset());
    }
}
