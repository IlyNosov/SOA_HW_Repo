package org.ilynosov.hw6.producer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderItemRequest(
    @JsonProperty("product_id")
    String productId,

    @JsonProperty("zone_id")
    String zoneId,

    Integer quantity
) {
}
