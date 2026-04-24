package org.ilynosov.hw5.producer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class SchemaRegistryInitializer implements ApplicationRunner {

    @Value("${schema-registry.url}")
    private String schemaRegistryUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String protoContent = new ClassPathResource("movie_event.proto")
            .getContentAsString(StandardCharsets.UTF_8);

        String escaped = protoContent
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "");

        String body = """
            {"schemaType":"PROTOBUF","schema":"%s"}
            """.formatted(escaped).strip();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/vnd.schemaregistry.v1+json"));

        try {
            String url = schemaRegistryUrl + "/subjects/movie-events-value/versions";
            restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
            log.info("Schema registered in Schema Registry");
        } catch (Exception e) {
            log.warn("Schema registration failed (may already exist): {}", e.getMessage());
        }
    }
}
