package org.ilynosov.hw6.consumer.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import lombok.RequiredArgsConstructor;
import org.ilynosov.hw6.consumer.config.CassandraProperties;
import org.ilynosov.hw6.consumer.model.ProcessedEvent;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProcessedEventRepository {

    private final CqlSession cqlSession;
    private final CassandraProperties properties;

    public boolean existsById(String eventId) {
        Row row = cqlSession.execute("""
            SELECT event_id
            FROM %s.processed_events
            WHERE event_id = ?
            """.formatted(properties.keyspace()), eventId).one();
        return row != null;
    }

    public Optional<ProcessedEvent> findById(String eventId) {
        Row row = cqlSession.execute("""
            SELECT event_id, event_type, processed_at, source_partition, source_offset
            FROM %s.processed_events
            WHERE event_id = ?
            """.formatted(properties.keyspace()), eventId).one();

        return Optional.ofNullable(row)
            .map(result -> new ProcessedEvent(
                result.getString("event_id"),
                result.getString("event_type"),
                result.getInstant("processed_at"),
                result.getInt("source_partition"),
                result.getLong("source_offset")
            ));
    }

    public void save(ProcessedEvent event) {
        cqlSession.execute("""
            INSERT INTO %s.processed_events
            (event_id, event_type, processed_at, source_partition, source_offset)
            VALUES (?, ?, ?, ?, ?)
            """.formatted(properties.keyspace()),
            event.eventId(),
            event.eventType(),
            event.processedAt() == null ? Instant.now() : event.processedAt(),
            event.sourcePartition(),
            event.sourceOffset());
    }
}
