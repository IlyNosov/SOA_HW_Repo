package org.ilynosov.hw6.producer.config;

import org.apache.avro.Schema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

@Configuration
public class KafkaProducerConfig {

    @Bean
    Schema warehouseEventSchema() throws IOException {
        return new Schema.Parser().parse(new ClassPathResource("avro/warehouse_event.avsc").getInputStream());
    }
}
