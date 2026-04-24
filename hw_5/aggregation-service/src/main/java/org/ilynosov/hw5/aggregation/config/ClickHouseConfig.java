package org.ilynosov.hw5.aggregation.config;

import org.ilynosov.hw5.aggregation.clickhouse.ClickHouseHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ClickHouseConfig {

    @Value("${clickhouse.url}")
    private String url;

    @Bean
    public ClickHouseHttpClient clickHouseHttpClient() {
        return new ClickHouseHttpClient(new RestTemplate(), url);
    }
}
