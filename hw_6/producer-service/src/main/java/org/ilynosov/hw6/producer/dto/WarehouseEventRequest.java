package org.ilynosov.hw6.producer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public record WarehouseEventRequest(
    @JsonProperty("event_id")
    String eventId,

    @NotBlank
    @JsonProperty("event_type")
    String eventType,

    @JsonProperty("product_id")
    String productId,

    @JsonProperty("zone_id")
    String zoneId,

    @JsonProperty("from_zone_id")
    String fromZoneId,

    @JsonProperty("to_zone_id")
    String toZoneId,

    @JsonProperty("order_id")
    String orderId,

    Integer quantity,

    @JsonProperty("order_items")
    List<OrderItemRequest> orderItems,

    @JsonProperty("event_timestamp")
    Instant eventTimestamp
) {
}
