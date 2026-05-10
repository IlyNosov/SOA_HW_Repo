package org.ilynosov.hw6.consumer.model;

import java.time.Instant;

public record InventoryItem(
    String productId,
    String zoneId,
    int availableQuantity,
    int reservedQuantity,
    Instant lastEventTimestamp,
    Instant updatedAt,
    String supplierId
) {
}
