package org.ilynosov.hw6.consumer.service;

import org.apache.avro.generic.GenericRecord;
import org.ilynosov.hw6.consumer.model.WarehouseEventData;
import org.ilynosov.hw6.consumer.model.WarehouseEventType;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class WarehouseEventMapper {

    public WarehouseEventData fromRecord(GenericRecord record) {
        return new WarehouseEventData(
            stringValue(record, "event_id"),
            WarehouseEventType.valueOf(stringValue(record, "event_type")),
            stringValue(record, "product_id"),
            stringValue(record, "zone_id"),
            stringValue(record, "from_zone_id"),
            stringValue(record, "to_zone_id"),
            stringValue(record, "order_id"),
            intValue(record, "quantity"),
            stringValue(record, "order_items_json"),
            Instant.parse(stringValue(record, "event_timestamp"))
        );
    }

    private String stringValue(GenericRecord record, String field) {
        Object value = record.get(field);
        return value == null ? null : value.toString();
    }

    private Integer intValue(GenericRecord record, String field) {
        Object value = record.get(field);
        return value == null ? null : (Integer) value;
    }
}
