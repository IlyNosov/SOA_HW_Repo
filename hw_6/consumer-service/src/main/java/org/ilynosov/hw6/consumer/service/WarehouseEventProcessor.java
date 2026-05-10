package org.ilynosov.hw6.consumer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ilynosov.hw6.consumer.exception.WarehouseEventBusinessException;
import org.ilynosov.hw6.consumer.model.EventProcessingResult;
import org.ilynosov.hw6.consumer.model.InventoryItem;
import org.ilynosov.hw6.consumer.model.OrderItem;
import org.ilynosov.hw6.consumer.model.ProcessedEvent;
import org.ilynosov.hw6.consumer.model.ProductInventory;
import org.ilynosov.hw6.consumer.model.WarehouseEventData;
import org.ilynosov.hw6.consumer.model.WarehouseEventHistoryItem;
import org.ilynosov.hw6.consumer.model.WarehouseOrder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseEventProcessor {

    private final WarehouseStateService stateService;
    private final ObjectMapper objectMapper;

    public void process(WarehouseEventData event, String payload, int partition, long offset) {
        validateEvent(event);

        if (stateService.isProcessed(event.eventId())) {
            log.info("Skip duplicate event_id={} event_type={}", event.eventId(), event.eventType());
            return;
        }

        EventProcessingResult result = isOutOfOrder(event)
            ? ignoredResult(event, payload, partition, offset)
            : applyEvent(event, payload, partition, offset);

        stateService.applyBatch(result);
    }

    private EventProcessingResult applyEvent(WarehouseEventData event, String payload, int partition, long offset) {
        ChangeSet changes = new ChangeSet(event.eventTimestamp());
        WarehouseOrder order = null;

        switch (event.eventType()) {
            case PRODUCT_RECEIVED -> changes.changeInventory(event.productId(), event.zoneId(), event.quantity(), 0);
            case PRODUCT_SHIPPED -> changes.changeInventory(event.productId(), event.zoneId(), -event.quantity(), 0);
            case PRODUCT_MOVED -> {
                changes.changeInventory(event.productId(), event.fromZoneId(), -event.quantity(), 0);
                changes.changeInventory(event.productId(), event.toZoneId(), event.quantity(), 0);
            }
            case PRODUCT_RESERVED -> changes.changeInventory(event.productId(), event.zoneId(), -event.quantity(), event.quantity());
            case PRODUCT_RELEASED -> changes.changeInventory(event.productId(), event.zoneId(), event.quantity(), -event.quantity());
            case INVENTORY_COUNTED -> changes.countInventory(event.productId(), event.zoneId(), event.quantity());
            case ORDER_CREATED -> {
                List<OrderItem> items = parseOrderItems(event);
                Instant now = Instant.now();
                order = new WarehouseOrder(event.orderId(), "CREATED", event.orderItemsJson(), now, now, event.eventTimestamp());
                for (OrderItem item : items) {
                    changes.changeInventory(item.productId(), item.zoneId(), -item.quantity(), item.quantity());
                }
            }
            case ORDER_COMPLETED -> {
                WarehouseOrder currentOrder = stateService.findOrder(event.orderId())
                    .orElseThrow(() -> new WarehouseEventBusinessException("Order not found: " + event.orderId()));
                List<OrderItem> items = parseOrderItems(currentOrder.itemsJson());
                Instant now = Instant.now();
                order = new WarehouseOrder(
                    currentOrder.orderId(),
                    "COMPLETED",
                    currentOrder.itemsJson(),
                    currentOrder.createdAt(),
                    now,
                    event.eventTimestamp()
                );
                for (OrderItem item : items) {
                    changes.changeInventory(item.productId(), item.zoneId(), 0, -item.quantity());
                }
            }
        }

        return new EventProcessingResult(
            changes.inventoryItems(),
            changes.productInventories(),
            order,
            history(event, payload, partition, offset),
            processed(event, partition, offset)
        );
    }

    private EventProcessingResult ignoredResult(WarehouseEventData event, String payload, int partition, long offset) {
        log.info("Ignore out-of-order event_id={} event_type={} event_timestamp={}",
            event.eventId(), event.eventType(), event.eventTimestamp());
        return new EventProcessingResult(
            List.of(),
            List.of(),
            null,
            history(event, payload, partition, offset),
            processed(event, partition, offset)
        );
    }

    private boolean isOutOfOrder(WarehouseEventData event) {
        for (ProductZone productZone : affectedProductZones(event)) {
            InventoryItem current = currentInventory(productZone.productId(), productZone.zoneId());
            if (current.lastEventTimestamp() != null && event.eventTimestamp().isBefore(current.lastEventTimestamp())) {
                return true;
            }
        }
        return false;
    }

    private List<ProductZone> affectedProductZones(WarehouseEventData event) {
        return switch (event.eventType()) {
            case PRODUCT_RECEIVED, PRODUCT_SHIPPED, PRODUCT_RESERVED, PRODUCT_RELEASED, INVENTORY_COUNTED ->
                List.of(new ProductZone(event.productId(), event.zoneId()));
            case PRODUCT_MOVED ->
                List.of(new ProductZone(event.productId(), event.fromZoneId()), new ProductZone(event.productId(), event.toZoneId()));
            case ORDER_CREATED -> parseOrderItems(event).stream()
                .map(item -> new ProductZone(item.productId(), item.zoneId()))
                .toList();
            case ORDER_COMPLETED -> stateService.findOrder(event.orderId())
                .map(order -> parseOrderItems(order.itemsJson()).stream()
                    .map(item -> new ProductZone(item.productId(), item.zoneId()))
                    .toList())
                .orElse(List.of());
        };
    }

    private InventoryItem currentInventory(String productId, String zoneId) {
        return stateService.findInventoryByProductAndZone(productId, zoneId)
            .orElse(new InventoryItem(productId, zoneId, 0, 0, null, null, null));
    }

    private ProductInventory currentProductInventory(String productId) {
        return stateService.findProductTotals(productId)
            .orElse(new ProductInventory(productId, 0, 0, null, null));
    }

    private List<OrderItem> parseOrderItems(WarehouseEventData event) {
        return parseOrderItems(event.orderItemsJson());
    }

    private List<OrderItem> parseOrderItems(String itemsJson) {
        if (itemsJson == null || itemsJson.isBlank()) {
            throw new WarehouseEventBusinessException("Order items are required");
        }
        try {
            return objectMapper.readValue(itemsJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new WarehouseEventBusinessException("Cannot parse order items", ex);
        }
    }

    private WarehouseEventHistoryItem history(WarehouseEventData event, String payload, int partition, long offset) {
        return new WarehouseEventHistoryItem(
            historyPartitionKey(event),
            event.eventTimestamp(),
            event.eventId(),
            event.eventType().name(),
            resolveHistoryZone(event),
            payload,
            partition,
            offset
        );
    }

    private ProcessedEvent processed(WarehouseEventData event, int partition, long offset) {
        return new ProcessedEvent(event.eventId(), event.eventType().name(), Instant.now(), partition, offset);
    }

    private void validateEvent(WarehouseEventData event) {
        require(event.eventId(), "event_id");
        require(event.eventTimestamp(), "event_timestamp");

        switch (event.eventType()) {
            case PRODUCT_RECEIVED, PRODUCT_SHIPPED, PRODUCT_RESERVED, PRODUCT_RELEASED, INVENTORY_COUNTED -> {
                require(event.productId(), "product_id");
                require(event.zoneId(), "zone_id");
                requirePositiveQuantity(event.quantity());
            }
            case PRODUCT_MOVED -> {
                require(event.productId(), "product_id");
                require(event.fromZoneId(), "from_zone_id");
                require(event.toZoneId(), "to_zone_id");
                requirePositiveQuantity(event.quantity());
            }
            case ORDER_CREATED -> {
                require(event.orderId(), "order_id");
                parseOrderItems(event);
            }
            case ORDER_COMPLETED -> require(event.orderId(), "order_id");
        }
    }

    private String historyPartitionKey(WarehouseEventData event) {
        if (event.productId() != null && !event.productId().isBlank()) {
            return event.productId();
        }
        return "ORDER#" + event.orderId();
    }

    private String resolveHistoryZone(WarehouseEventData event) {
        if (event.zoneId() != null) {
            return event.zoneId();
        }
        if (event.fromZoneId() != null && event.toZoneId() != null) {
            return event.fromZoneId() + "->" + event.toZoneId();
        }
        return null;
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new WarehouseEventBusinessException(field + " is required");
        }
    }

    private void require(Object value, String field) {
        if (value == null) {
            throw new WarehouseEventBusinessException(field + " is required");
        }
    }

    private void requirePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new WarehouseEventBusinessException("quantity must be positive");
        }
    }

    private record ProductZone(String productId, String zoneId) {
    }

    private class ChangeSet {

        private final Instant eventTimestamp;
        private final Map<String, InventoryItem> inventoryByKey = new LinkedHashMap<>();
        private final Map<String, int[]> productDeltas = new HashMap<>();

        private ChangeSet(Instant eventTimestamp) {
            this.eventTimestamp = eventTimestamp;
        }

        private void changeInventory(String productId, String zoneId, int availableDelta, int reservedDelta) {
            String key = productId + "|" + zoneId;
            InventoryItem current = inventoryByKey.getOrDefault(key, currentInventory(productId, zoneId));
            int nextAvailable = current.availableQuantity() + availableDelta;
            int nextReserved = current.reservedQuantity() + reservedDelta;

            if (nextAvailable < 0) {
                throw new WarehouseEventBusinessException("Not enough available quantity for product_id=" + productId + " zone_id=" + zoneId);
            }
            if (nextReserved < 0) {
                throw new WarehouseEventBusinessException("Not enough reserved quantity for product_id=" + productId + " zone_id=" + zoneId);
            }

            inventoryByKey.put(key, new InventoryItem(
                productId,
                zoneId,
                nextAvailable,
                nextReserved,
                eventTimestamp,
                Instant.now(),
                current.supplierId()
            ));
            addProductDelta(productId, availableDelta, reservedDelta);
        }

        private void countInventory(String productId, String zoneId, int countedQuantity) {
            String key = productId + "|" + zoneId;
            InventoryItem current = inventoryByKey.getOrDefault(key, currentInventory(productId, zoneId));
            int availableDelta = countedQuantity - current.availableQuantity();

            inventoryByKey.put(key, new InventoryItem(
                productId,
                zoneId,
                countedQuantity,
                current.reservedQuantity(),
                eventTimestamp,
                Instant.now(),
                current.supplierId()
            ));
            addProductDelta(productId, availableDelta, 0);
        }

        private void addProductDelta(String productId, int availableDelta, int reservedDelta) {
            int[] delta = productDeltas.computeIfAbsent(productId, ignored -> new int[2]);
            delta[0] += availableDelta;
            delta[1] += reservedDelta;
        }

        private List<InventoryItem> inventoryItems() {
            return new ArrayList<>(inventoryByKey.values());
        }

        private List<ProductInventory> productInventories() {
            List<ProductInventory> result = new ArrayList<>();
            for (Map.Entry<String, int[]> entry : productDeltas.entrySet()) {
                ProductInventory current = currentProductInventory(entry.getKey());
                int nextAvailable = current.totalAvailableQuantity() + entry.getValue()[0];
                int nextReserved = current.totalReservedQuantity() + entry.getValue()[1];

                if (nextAvailable < 0) {
                    throw new WarehouseEventBusinessException("Total available quantity became negative for product_id=" + entry.getKey());
                }
                if (nextReserved < 0) {
                    throw new WarehouseEventBusinessException("Total reserved quantity became negative for product_id=" + entry.getKey());
                }

                result.add(new ProductInventory(entry.getKey(), nextAvailable, nextReserved, eventTimestamp, Instant.now()));
            }
            return result;
        }
    }
}
