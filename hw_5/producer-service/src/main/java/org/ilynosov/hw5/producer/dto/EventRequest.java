package org.ilynosov.hw5.producer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EventRequest(
    @NotBlank String userId,
    @NotBlank String movieId,
    @NotNull EventTypeDto eventType,
    @NotNull DeviceTypeDto deviceType,
    @NotBlank String sessionId,
    int progressSeconds
) {
    public enum EventTypeDto {
        VIEW_STARTED, VIEW_FINISHED, VIEW_PAUSED, VIEW_RESUMED, LIKED, SEARCHED
    }

    public enum DeviceTypeDto {
        MOBILE, DESKTOP, TV, TABLET
    }
}
