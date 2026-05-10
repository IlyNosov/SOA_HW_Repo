package org.ilynosov.hw6.consumer.model;

import java.util.List;

public record EventProcessingResult(
    List<InventoryItem> inventoryItems,
    List<ProductInventory> productInventories,
    WarehouseOrder order,
    WarehouseEventHistoryItem historyItem,
    ProcessedEvent processedEvent
) {
}
