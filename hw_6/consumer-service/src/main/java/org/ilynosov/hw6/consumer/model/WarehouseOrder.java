package org.ilynosov.hw6.consumer.model;

import java.time.Instant;

public record WarehouseOrder(
    String orderId,
    String status,
    String itemsJson,
    Instant createdAt,
    Instant updatedAt,
    Instant lastEventTimestamp
) {
}
