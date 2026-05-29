package org.ilynosov.hw6.producer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.ilynosov.hw6.producer.dto.WarehouseEventRequest;
import org.ilynosov.hw6.producer.dto.WarehouseEventResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseEventProducer {

    private final KafkaTemplate<String, GenericRecord> kafkaTemplate;
    private final Schema warehouseEventSchema;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.warehouse-events-topic}")
    private String topic;

    public WarehouseEventResponse publish(WarehouseEventRequest request) {
        String eventId = request.eventId() == null || request.eventId().isBlank()
            ? UUID.randomUUID().toString()
            : request.eventId();
        Instant eventTimestamp = request.eventTimestamp() == null ? Instant.now() : request.eventTimestamp();

        GenericRecord record = new GenericData.Record(warehouseEventSchema);
        record.put("event_id", eventId);
        record.put("event_type", request.eventType());
        record.put("product_id", request.productId());
        record.put("zone_id", request.zoneId());
        record.put("from_zone_id", request.fromZoneId());
        record.put("to_zone_id", request.toZoneId());
        record.put("order_id", request.orderId());
        record.put("quantity", request.quantity());
        record.put("order_items_json", orderItemsJson(request));
        record.put("event_timestamp", eventTimestamp.toString());

        var metadata = kafkaTemplate.send(topic, eventId, record).join().getRecordMetadata();
        return new WarehouseEventResponse(
            eventId,
            request.eventType(),
            metadata.topic(),
            metadata.partition(),
            metadata.offset(),
            eventTimestamp
        );
    }

    private String orderItemsJson(WarehouseEventRequest request) {
        if (request.orderItems() == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(request.orderItems());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Cannot serialize order_items", ex);
        }
    }
}
