package org.ilynosov.hw5.aggregation.repository;

import org.ilynosov.hw5.aggregation.entity.DailyMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MetricsRepository extends JpaRepository<DailyMetric, Long> {

    List<DailyMetric> findByDate(LocalDate date);

    @Modifying
    @Query(value = """
        INSERT INTO daily_metrics (date, metric_name, metric_value, metric_json, computed_at)
        VALUES (:date, :name, :value, :json, NOW())
        ON CONFLICT (date, metric_name) DO UPDATE SET
            metric_value = EXCLUDED.metric_value,
            metric_json  = EXCLUDED.metric_json,
            computed_at  = EXCLUDED.computed_at
        """, nativeQuery = true)
    void upsert(
        @Param("date") LocalDate date,
        @Param("name") String name,
        @Param("value") Double value,
        @Param("json") String json
    );
}
