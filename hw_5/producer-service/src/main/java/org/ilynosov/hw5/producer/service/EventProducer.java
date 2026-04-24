package org.ilynosov.hw5.producer.service;

import com.google.protobuf.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ilynosov.hw5.producer.proto.MovieEventProto.MovieEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventProducer {

    private static final String TOPIC = "movie-events";
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_DELAY_MS = 500;

    private final KafkaTemplate<String, Message> kafkaTemplate;

    public String publish(MovieEvent event) {
        String eventId = event.getEventId();
        long delay = INITIAL_DELAY_MS;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                kafkaTemplate.send(TOPIC, event.getUserId(), event).get(10, TimeUnit.SECONDS);
                log.info("Published event_id={} event_type={} timestamp={}",
                    eventId, event.getEventType(), event.getTimestamp());
                return eventId;
            } catch (Exception ex) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Failed to publish event_id={} after {} attempts: {}", eventId, MAX_ATTEMPTS, ex.getMessage());
                    throw new RuntimeException("Kafka publish failed after retries", ex);
                }
                log.warn("Publish attempt {}/{} failed for event_id={}, retrying in {}ms: {}",
                    attempt, MAX_ATTEMPTS, eventId, delay, ex.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
                delay *= 2;
            }
        }
        return eventId;
    }
}
