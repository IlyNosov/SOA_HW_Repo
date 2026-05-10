package org.ilynosov.hw6.consumer.model;

import java.time.Instant;

public record ProductInventory(
    String productId,
    int totalAvailableQuantity,
    int totalReservedQuantity,
    Instant lastEventTimestamp,
    Instant updatedAt
) {
}
