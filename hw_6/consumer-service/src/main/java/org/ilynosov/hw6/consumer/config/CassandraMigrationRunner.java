package org.ilynosov.hw6.consumer.config;

import com.datastax.oss.driver.api.core.CqlSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.cassandra.migrations", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CassandraMigrationRunner implements ApplicationRunner {

    private final CqlSession cqlSession;
    private final CassandraProperties properties;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        ClassPathResource resource = new ClassPathResource("cassandra/schema.cql");
        String cql = resource.getContentAsString(StandardCharsets.UTF_8)
            .replace("${keyspace}", properties.keyspace());

        for (String statement : cql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isBlank()) {
                cqlSession.execute(trimmed);
            }
        }

        log.info("Applied Cassandra schema for keyspace={}", properties.keyspace());
    }
}
