package org.ilynosov.hw6.consumer.model;

import java.time.Instant;

public record WarehouseEventHistoryItem(
    String productId,
    Instant eventTimestamp,
    String eventId,
    String eventType,
    String zoneId,
    String payload,
    int sourcePartition,
    long sourceOffset
) {
}
