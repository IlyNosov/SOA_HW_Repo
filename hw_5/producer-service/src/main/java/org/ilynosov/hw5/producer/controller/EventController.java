package org.ilynosov.hw5.producer.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ilynosov.hw5.producer.dto.EventRequest;
import org.ilynosov.hw5.producer.proto.MovieEventProto.DeviceType;
import org.ilynosov.hw5.producer.proto.MovieEventProto.EventType;
import org.ilynosov.hw5.producer.proto.MovieEventProto.MovieEvent;
import org.ilynosov.hw5.producer.service.EventProducer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventProducer producer;

    @PostMapping
    public ResponseEntity<Map<String, String>> publish(@RequestBody @Valid EventRequest request) {
        MovieEvent event = MovieEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setUserId(request.userId())
            .setMovieId(request.movieId())
            .setEventType(EventType.valueOf(request.eventType().name()))
            .setTimestamp(Instant.now().toEpochMilli())
            .setDeviceType(DeviceType.valueOf(request.deviceType().name()))
            .setSessionId(request.sessionId())
            .setProgressSeconds(request.progressSeconds())
            .build();

        String eventId = producer.publish(event);
        return ResponseEntity.ok(Map.of("event_id", eventId));
    }
}
