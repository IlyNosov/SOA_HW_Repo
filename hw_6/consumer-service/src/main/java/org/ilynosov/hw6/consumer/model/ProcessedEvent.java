package org.ilynosov.hw6.consumer.model;

import java.time.Instant;

public record ProcessedEvent(
    String eventId,
    String eventType,
    Instant processedAt,
    int sourcePartition,
    long sourceOffset
) {
}
