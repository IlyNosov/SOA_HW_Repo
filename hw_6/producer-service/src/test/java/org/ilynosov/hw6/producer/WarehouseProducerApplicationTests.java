package org.ilynosov.hw6.producer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.kafka.bootstrap-servers=localhost:19092",
    "app.schema-registry-url=mock://hw6-producer-test"
})
class WarehouseProducerApplicationTests {

    @Test
    void contextLoads() {
    }
}
