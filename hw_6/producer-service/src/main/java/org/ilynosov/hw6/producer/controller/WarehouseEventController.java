package org.ilynosov.hw6.producer.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ilynosov.hw6.producer.dto.WarehouseEventRequest;
import org.ilynosov.hw6.producer.dto.WarehouseEventResponse;
import org.ilynosov.hw6.producer.service.WarehouseEventProducer;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class WarehouseEventController {

    private final WarehouseEventProducer producer;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WarehouseEventResponse publish(@Valid @RequestBody WarehouseEventRequest request) {
        return producer.publish(request);
    }
}
