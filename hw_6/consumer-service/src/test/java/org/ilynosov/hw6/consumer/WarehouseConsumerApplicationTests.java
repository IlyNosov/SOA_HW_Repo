package org.ilynosov.hw6.consumer;

import com.datastax.oss.driver.api.core.CqlSession;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.lang.reflect.Proxy;

@SpringBootTest(properties = {
    "spring.kafka.bootstrap-servers=localhost:19092",
    "spring.kafka.listener.auto-startup=false",
    "spring.main.allow-bean-definition-overriding=true",
    "app.schema-registry-url=mock://hw6-consumer-test",
    "app.cassandra.contact-points=localhost:9042",
    "app.cassandra.local-datacenter=datacenter1",
    "app.cassandra.keyspace=warehouse",
    "app.cassandra.migrations.enabled=false"
})
class WarehouseConsumerApplicationTests {

    @Test
    void contextLoads() {
    }

    @TestConfiguration
    static class TestCassandraConfig {

        @Bean
        @Primary
        CqlSession cqlSession() {
            return (CqlSession) Proxy.newProxyInstance(
                CqlSession.class.getClassLoader(),
                new Class<?>[]{CqlSession.class},
                (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) {
                        return "test-cql-session";
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    throw new UnsupportedOperationException("CqlSession is not used in this context test");
                }
            );
        }
    }
}
