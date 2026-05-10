package org.ilynosov.hw6.consumer.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.ilynosov.hw6.consumer.exception.WarehouseEventBusinessException;
import org.ilynosov.hw6.consumer.model.WarehouseEventData;
import org.ilynosov.hw6.consumer.service.DlqProducer;
import org.ilynosov.hw6.consumer.service.WarehouseEventMapper;
import org.ilynosov.hw6.consumer.service.WarehouseEventProcessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseEventConsumer {

    private final WarehouseEventMapper mapper;
    private final WarehouseEventProcessor processor;
    private final DlqProducer dlqProducer;

    @KafkaListener(topics = "warehouse-events", groupId = "warehouse-state-consumer")
    public void consume(ConsumerRecord<String, GenericRecord> record, Acknowledgment acknowledgment) {
        String originalEvent = record.value() == null ? null : record.value().toString();

        try {
            WarehouseEventData event = mapper.fromRecord(record.value());
            processor.process(event, originalEvent, record.partition(), record.offset());
            acknowledgment.acknowledge();

            log.info("Processed event_id={} event_type={} partition={} offset={}",
                event.eventId(), event.eventType(), record.partition(), record.offset());
        } catch (WarehouseEventBusinessException | IllegalArgumentException ex) {
            dlqProducer.send(record, originalEvent, ex);
            acknowledgment.acknowledge();
            log.warn("Sent event to DLQ partition={} offset={} reason={}",
                record.partition(), record.offset(), ex.getMessage());
        }
    }
}
