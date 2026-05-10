package org.ilynosov.hw6.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class DlqProducer {

    private static final String DLQ_TOPIC = "warehouse-events-dlq";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void send(ConsumerRecord<String, ?> record, String originalEvent, Exception exception) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "original_event", originalEvent == null ? "null" : originalEvent,
                "error_reason", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                "error_code", "PROCESSING_ERROR",
                "failed_at", Instant.now().toString(),
                "kafka_metadata", Map.of(
                    "topic", record.topic(),
                    "partition", record.partition(),
                    "offset", record.offset()
                )
            ));
            kafkaTemplate.send(DLQ_TOPIC, record.key(), payload).get(10, TimeUnit.SECONDS);
        } catch (Exception dlqException) {
            throw new IllegalStateException("Failed to publish event to DLQ", dlqException);
        }
    }
}
