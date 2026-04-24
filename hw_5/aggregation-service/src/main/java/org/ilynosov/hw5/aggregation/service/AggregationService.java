package org.ilynosov.hw5.aggregation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ilynosov.hw5.aggregation.clickhouse.ClickHouseHttpClient;
import org.ilynosov.hw5.aggregation.repository.MetricsRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AggregationService {

    private final ClickHouseHttpClient clickHouse;
    private final MetricsRepository metricsRepository;
    private final S3ExportService s3ExportService;

    @Scheduled(cron = "${aggregation.cron:0 0 * * * *}")
    public void runScheduled() {
        compute(LocalDate.now().minusDays(1));
    }

    @Transactional
    public void compute(LocalDate date) {
        long start = System.currentTimeMillis();
        log.info("Aggregation started for {}", date);

        String d = date.toString();

        long dau = computeDau(d);
        double avgViewTime = computeAvgViewTime(d);
        double conversion = computeConversion(d);
        List<Map<String, Object>> topMovies = computeTopMovies(d);
        Map<String, Double> retention = computeRetention(d);

        metricsRepository.upsert(date, "dau", (double) dau, null);
        metricsRepository.upsert(date, "avg_view_time_seconds", avgViewTime, null);
        metricsRepository.upsert(date, "view_conversion", conversion, null);
        metricsRepository.upsert(date, "retention_d1", retention.get("d1"), null);
        metricsRepository.upsert(date, "retention_d7", retention.get("d7"), null);

        String topMoviesJson = topMovies.stream()
            .map(Object::toString)
            .collect(Collectors.joining(",", "[", "]"));
        metricsRepository.upsert(date, "top_movies", null, topMoviesJson);

        writeToClickHouse(d, dau, avgViewTime, conversion, retention);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Aggregation for {} done in {}ms: DAU={}, avgViewTime={}s, conversion={}",
            date, elapsed, dau,
            String.format("%.1f", avgViewTime),
            String.format("%.2f", conversion));

        try {
            s3ExportService.export(date);
        } catch (Exception e) {
            log.error("S3 export failed for {}: {}", date, e.getMessage());
        }
    }

    private long computeDau(String date) {
        return clickHouse.queryForLong(
            "SELECT uniq(user_id) FROM movie_events WHERE date = toDate('" + date + "')");
    }

    private double computeAvgViewTime(String date) {
        return clickHouse.queryForDouble(
            "SELECT avg(progress_seconds) FROM movie_events" +
            " WHERE date = toDate('" + date + "') AND event_type = 'VIEW_FINISHED'");
    }

    private double computeConversion(String date) {
        return clickHouse.queryForDouble(
            "SELECT countIf(event_type = 'VIEW_FINISHED') /" +
            " if(countIf(event_type = 'VIEW_STARTED') > 0," +
            "    countIf(event_type = 'VIEW_STARTED'), 1)" +
            " FROM movie_events WHERE date = toDate('" + date + "')");
    }

    private List<Map<String, Object>> computeTopMovies(String date) {
        return clickHouse.queryForList(
            "SELECT movie_id, uniq(user_id) AS viewers, count() AS views" +
            " FROM movie_events WHERE date = toDate('" + date + "')" +
            " AND event_type = 'VIEW_STARTED'" +
            " GROUP BY movie_id ORDER BY viewers DESC LIMIT 10");
    }

    private Map<String, Double> computeRetention(String date) {
        Map<String, Object> row = clickHouse.queryForMap(
            "WITH first_views AS (" +
            "  SELECT user_id, toDate(min(timestamp)) AS first_date" +
            "  FROM movie_events WHERE event_type = 'VIEW_STARTED' GROUP BY user_id" +
            ")," +
            "cohort AS (" +
            "  SELECT fv.user_id, fv.first_date, toDate(me.timestamp) AS activity_date" +
            "  FROM first_views fv" +
            "  INNER JOIN movie_events me ON fv.user_id = me.user_id" +
            "  WHERE fv.first_date = toDate('" + date + "')" +
            "  GROUP BY fv.user_id, fv.first_date, activity_date" +
            ")" +
            "SELECT" +
            "  uniq(user_id) AS cohort_size," +
            "  uniqIf(user_id, dateDiff('day', first_date, activity_date) = 1) AS d1_retained," +
            "  uniqIf(user_id, dateDiff('day', first_date, activity_date) = 7) AS d7_retained," +
            "  d1_retained / if(cohort_size > 0, cohort_size, 1) AS retention_d1," +
            "  d7_retained / if(cohort_size > 0, cohort_size, 1) AS retention_d7" +
            " FROM cohort");

        if (row.isEmpty()) return Map.of("d1", 0.0, "d7", 0.0);
        return Map.of(
            "d1", toDouble(row.get("retention_d1")),
            "d7", toDouble(row.get("retention_d7"))
        );
    }

    private void writeToClickHouse(String date, long dau, double avgViewTime,
                                   double conversion, Map<String, Double> retention) {
        clickHouse.execute("INSERT INTO daily_aggregates (date, metric_name, metric_value) VALUES" +
            " ('" + date + "', 'dau', " + dau + ")," +
            " ('" + date + "', 'avg_view_time_seconds', " + avgViewTime + ")," +
            " ('" + date + "', 'view_conversion', " + conversion + ")," +
            " ('" + date + "', 'retention_d1', " + retention.get("d1") + ")," +
            " ('" + date + "', 'retention_d7', " + retention.get("d7") + ")");
    }

    private static double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0.0; }
    }
}
