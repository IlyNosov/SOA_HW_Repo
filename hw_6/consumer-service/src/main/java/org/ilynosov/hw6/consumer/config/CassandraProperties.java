package org.ilynosov.hw6.consumer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cassandra")
public record CassandraProperties(
    String contactPoints,
    String localDatacenter,
    String keyspace
) {
}
