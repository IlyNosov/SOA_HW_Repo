package org.ilynosov.hw5.aggregation.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ilynosov.hw5.aggregation.entity.DailyMetric;
import org.ilynosov.hw5.aggregation.repository.MetricsRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3ExportService {

    private static final String BUCKET = "movie-analytics";
    private static final int MAX_ATTEMPTS = 3;

    private final MinioClient minioClient;
    private final MetricsRepository metricsRepository;

    private static String toJson(List<DailyMetric> metrics) {
        String entries = metrics.stream().map(m -> """
            {"date":"%s","metric_name":"%s","metric_value":%s,"computed_at":"%s"}""".formatted(
            m.getDate(),
            m.getMetricName(),
            m.getMetricValue() != null ? m.getMetricValue() : "null",
            m.getComputedAt()
        )).collect(Collectors.joining(","));
        return "[" + entries + "]";
    }

    public void export(LocalDate date) throws Exception {
        List<DailyMetric> metrics = metricsRepository.findByDate(date);
        if (metrics.isEmpty()) {
            log.info("No metrics for {} to export", date);
            return;
        }

        String json = toJson(metrics);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String key = "daily/" + date + "/aggregates.json";

        long delay = 1000;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(BUCKET)
                        .object(key)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType("application/json")
                        .build()
                );
                log.info("Exported {} metrics for {} to s3://{}/{}", metrics.size(), date, BUCKET, key);
                return;
            } catch (Exception ex) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("S3 export failed after {} attempts for {}: {}", MAX_ATTEMPTS, date, ex.getMessage());
                    throw ex;
                }
                log.warn("S3 export attempt {}/{} failed, retrying in {}ms: {}", attempt, MAX_ATTEMPTS, delay, ex.getMessage());
                Thread.sleep(delay);
                delay *= 2;
            }
        }
    }
}
