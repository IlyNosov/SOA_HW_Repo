package org.ilynosov.hw6.producer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record WarehouseEventResponse(
    @JsonProperty("event_id")
    String eventId,

    @JsonProperty("event_type")
    String eventType,

    String topic,

    Integer partition,

    Long offset,

    @JsonProperty("event_timestamp")
    Instant eventTimestamp
) {
}
