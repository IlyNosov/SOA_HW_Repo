package org.ilynosov.hw6.consumer.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import lombok.RequiredArgsConstructor;
import org.ilynosov.hw6.consumer.config.CassandraProperties;
import org.ilynosov.hw6.consumer.model.InventoryItem;
import org.ilynosov.hw6.consumer.model.ProductInventory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class InventoryRepository {

    private final CqlSession cqlSession;
    private final CassandraProperties properties;

    public Optional<InventoryItem> findByProductAndZone(String productId, String zoneId) {
        Row row = cqlSession.execute("""
            SELECT product_id, zone_id, available_quantity, reserved_quantity, last_event_timestamp, updated_at, supplier_id
            FROM %s.inventory_by_product_zone
            WHERE product_id = ? AND zone_id = ?
            """.formatted(properties.keyspace()), productId, zoneId).one();

        return Optional.ofNullable(row).map(this::mapInventoryItem);
    }

    public List<InventoryItem> findByProduct(String productId) {
        return cqlSession.execute("""
            SELECT product_id, zone_id, available_quantity, reserved_quantity, last_event_timestamp, updated_at, supplier_id
            FROM %s.inventory_by_product_zone
            WHERE product_id = ?
            """.formatted(properties.keyspace()), productId).all()
            .stream()
            .map(this::mapInventoryItem)
            .toList();
    }

    public List<InventoryItem> findByZone(String zoneId) {
        return cqlSession.execute("""
            SELECT product_id, zone_id, available_quantity, reserved_quantity, last_event_timestamp, updated_at, supplier_id
            FROM %s.inventory_by_zone
            WHERE zone_id = ?
            """.formatted(properties.keyspace()), zoneId).all()
            .stream()
            .map(this::mapInventoryItem)
            .toList();
    }

    public Optional<ProductInventory> findProductTotals(String productId) {
        Row row = cqlSession.execute("""
            SELECT product_id, total_available_quantity, total_reserved_quantity, last_event_timestamp, updated_at
            FROM %s.inventory_by_product
            WHERE product_id = ?
            """.formatted(properties.keyspace()), productId).one();

        return Optional.ofNullable(row).map(this::mapProductInventory);
    }

    public void saveProductZoneInventory(InventoryItem item) {
        cqlSession.execute("""
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
            item.supplierId());
    }

    public void saveZoneInventory(InventoryItem item) {
        cqlSession.execute("""
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
            item.supplierId());
    }

    public void saveProductTotals(ProductInventory inventory) {
        cqlSession.execute("""
            INSERT INTO %s.inventory_by_product
            (product_id, total_available_quantity, total_reserved_quantity, last_event_timestamp, updated_at)
            VALUES (?, ?, ?, ?, ?)
            """.formatted(properties.keyspace()),
            inventory.productId(),
            inventory.totalAvailableQuantity(),
            inventory.totalReservedQuantity(),
            inventory.lastEventTimestamp(),
            inventory.updatedAt());
    }

    private InventoryItem mapInventoryItem(Row row) {
        return new InventoryItem(
            row.getString("product_id"),
            row.getString("zone_id"),
            row.getInt("available_quantity"),
            row.getInt("reserved_quantity"),
            getInstant(row, "last_event_timestamp"),
            getInstant(row, "updated_at"),
            row.getString("supplier_id")
        );
    }

    private ProductInventory mapProductInventory(Row row) {
        return new ProductInventory(
            row.getString("product_id"),
            row.getInt("total_available_quantity"),
            row.getInt("total_reserved_quantity"),
            getInstant(row, "last_event_timestamp"),
            getInstant(row, "updated_at")
        );
    }

    private Instant getInstant(Row row, String column) {
        return row.isNull(column) ? null : row.getInstant(column);
    }
}
