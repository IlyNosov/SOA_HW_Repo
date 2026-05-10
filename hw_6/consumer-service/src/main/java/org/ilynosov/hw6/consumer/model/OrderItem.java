package org.ilynosov.hw6.consumer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderItem(
    @JsonProperty("product_id")
    String productId,

    @JsonProperty("zone_id")
    String zoneId,

    Integer quantity
) {
}
