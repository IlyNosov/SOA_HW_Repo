package org.ilynosov.hw6.consumer.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.DefaultBatchType;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import lombok.RequiredArgsConstructor;
import org.ilynosov.hw6.consumer.config.CassandraProperties;
import org.ilynosov.hw6.consumer.model.EventProcessingResult;
import org.ilynosov.hw6.consumer.model.InventoryItem;
import org.ilynosov.hw6.consumer.model.ProcessedEvent;
import org.ilynosov.hw6.consumer.model.ProductInventory;
import org.ilynosov.hw6.consumer.model.WarehouseEventHistoryItem;
import org.ilynosov.hw6.consumer.model.WarehouseOrder;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CassandraBatchRepository {

    private final CqlSession cqlSession;
    private final CassandraProperties properties;

    public void apply(EventProcessingResult result) {
        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED);

        for (InventoryItem item : result.inventoryItems()) {
            addInventoryItem(batch, item);
        }
        for (ProductInventory inventory : result.productInventories()) {
            addProductTotals(batch, inventory);
        }
        if (result.order() != null) {
            addOrder(batch, result.order());
        }
        addHistory(batch, result.historyItem());
        addProcessedEvent(batch, result.processedEvent());

        cqlSession.execute(batch.build());
    }

    private void addInventoryItem(BatchStatementBuilder batch, InventoryItem item) {
        batch.addStatement(SimpleStatement.newInstance("""
            INSERT INTO %s.inventory_by_product_zone
            (product_id, zone_id, available_quantity, reserved_quantity, last_event_timestamp, updated_at, supplier_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.formatted(properties.keyspace()),
            item.productId(),
            item.zoneId(),
            item.availableQuantity(),
            item.reservedQuantity(),
            item.lastEventTimestamp(),
            item.updatedAt(),
            item.supplierId()));

        batch.addStatement(SimpleStatement.newInstance("""
            INSERT INTO %s.inventory_by_zone
            (zone_id, product_id, available_quantity, reserved_quantity, last_event_timestamp, updated_at, supplier_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.formatted(properties.keyspace()),
            item.zoneId(),
            item.productId(),
            item.availableQuantity(),
            item.reservedQuantity(),
            item.lastEventTimestamp(),
            item.updatedAt(),
            item.supplierId()));
    }

    private void addProductTotals(BatchStatementBuilder batch, ProductInventory inventory) {
        batch.addStatement(SimpleStatement.newInstance("""
            INSERT INTO %s.inventory_by_product
            (product_id, total_available_quantity, total_reserved_quantity, last_event_timestamp, updated_at)
            VALUES (?, ?, ?, ?, ?)
            """.formatted(properties.keyspace()),
            inventory.productId(),
            inventory.totalAvailableQuantity(),
            inventory.totalReservedQuantity(),
            inventory.lastEventTimestamp(),
            inventory.updatedAt()));
    }

    private void addOrder(BatchStatementBuilder batch, WarehouseOrder order) {
        batch.addStatement(SimpleStatement.newInstance("""
            INSERT INTO %s.orders_by_id
            (order_id, status, items_json, created_at, updated_at, last_event_timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
            """.formatted(properties.keyspace()),
            order.orderId(),
            order.status(),
            order.itemsJson(),
            order.createdAt(),
            order.updatedAt(),
            order.lastEventTimestamp()));
    }

    private void addHistory(BatchStatementBuilder batch, WarehouseEventHistoryItem item) {
        batch.addStatement(SimpleStatement.newInstance("""
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
            item.sourceOffset()));
    }

    private void addProcessedEvent(BatchStatementBuilder batch, ProcessedEvent event) {
        batch.addStatement(SimpleStatement.newInstance("""
            INSERT INTO %s.processed_events
            (event_id, event_type, processed_at, source_partition, source_offset)
            VALUES (?, ?, ?, ?, ?)
            """.formatted(properties.keyspace()),
            event.eventId(),
            event.eventType(),
            event.processedAt(),
            event.sourcePartition(),
            event.sourceOffset()));
    }
}
