package org.ilynosov.hw6.consumer.service;

import lombok.RequiredArgsConstructor;
import org.ilynosov.hw6.consumer.model.EventProcessingResult;
import org.ilynosov.hw6.consumer.model.InventoryItem;
import org.ilynosov.hw6.consumer.model.ProcessedEvent;
import org.ilynosov.hw6.consumer.model.ProductInventory;
import org.ilynosov.hw6.consumer.model.WarehouseEventHistoryItem;
import org.ilynosov.hw6.consumer.model.WarehouseOrder;
import org.ilynosov.hw6.consumer.repository.CassandraBatchRepository;
import org.ilynosov.hw6.consumer.repository.InventoryRepository;
import org.ilynosov.hw6.consumer.repository.OrderRepository;
import org.ilynosov.hw6.consumer.repository.ProcessedEventRepository;
import org.ilynosov.hw6.consumer.repository.WarehouseEventHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WarehouseStateService {

    private final InventoryRepository inventoryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final WarehouseEventHistoryRepository eventHistoryRepository;
    private final OrderRepository orderRepository;
    private final CassandraBatchRepository cassandraBatchRepository;

    public Optional<InventoryItem> findInventoryByProductAndZone(String productId, String zoneId) {
        return inventoryRepository.findByProductAndZone(productId, zoneId);
    }

    public List<InventoryItem> findInventoryByProduct(String productId) {
        return inventoryRepository.findByProduct(productId);
    }

    public List<InventoryItem> findInventoryByZone(String zoneId) {
        return inventoryRepository.findByZone(zoneId);
    }

    public Optional<ProductInventory> findProductTotals(String productId) {
        return inventoryRepository.findProductTotals(productId);
    }

    public void saveInventoryState(InventoryItem item, ProductInventory productInventory) {
        inventoryRepository.saveProductZoneInventory(item);
        inventoryRepository.saveZoneInventory(item);
        inventoryRepository.saveProductTotals(productInventory);
    }

    public void saveInventoryItem(InventoryItem item) {
        inventoryRepository.saveProductZoneInventory(item);
        inventoryRepository.saveZoneInventory(item);
    }

    public void saveProductTotals(ProductInventory productInventory) {
        inventoryRepository.saveProductTotals(productInventory);
    }

    public boolean isProcessed(String eventId) {
        return processedEventRepository.existsById(eventId);
    }

    public void markProcessed(ProcessedEvent event) {
        processedEventRepository.save(event);
    }

    public void saveHistory(WarehouseEventHistoryItem item) {
        eventHistoryRepository.save(item);
    }

    public Optional<WarehouseOrder> findOrder(String orderId) {
        return orderRepository.findById(orderId);
    }

    public void saveOrder(WarehouseOrder order) {
        orderRepository.save(order);
    }

    public void applyBatch(EventProcessingResult result) {
        cassandraBatchRepository.apply(result);
    }
}
