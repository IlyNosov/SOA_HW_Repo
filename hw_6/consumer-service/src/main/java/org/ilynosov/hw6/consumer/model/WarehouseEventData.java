package org.ilynosov.hw6.consumer.model;

import java.time.Instant;

public record WarehouseEventData(
    String eventId,
    WarehouseEventType eventType,
    String productId,
    String zoneId,
    String fromZoneId,
    String toZoneId,
    String orderId,
    Integer quantity,
    String orderItemsJson,
    Instant eventTimestamp
) {
}
