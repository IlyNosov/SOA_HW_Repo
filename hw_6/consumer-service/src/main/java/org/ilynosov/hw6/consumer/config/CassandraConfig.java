package org.ilynosov.hw6.consumer.config;

import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;

@Configuration
@EnableConfigurationProperties(CassandraProperties.class)
public class CassandraConfig {

    @Bean
    public CqlSession cqlSession(CassandraProperties properties) {
        String[] hostAndPort = properties.contactPoints().split(":");
        return CqlSession.builder()
            .addContactPoint(new InetSocketAddress(hostAndPort[0], Integer.parseInt(hostAndPort[1])))
            .withLocalDatacenter(properties.localDatacenter())
            .build();
    }
}
