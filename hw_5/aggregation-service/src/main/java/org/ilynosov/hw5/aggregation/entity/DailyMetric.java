package org.ilynosov.hw5.aggregation.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "daily_metrics",
    uniqueConstraints = @UniqueConstraint(columnNames = {"date", "metric_name"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "metric_name", nullable = false, length = 100)
    private String metricName;

    @Column(name = "metric_value")
    private Double metricValue;

    @Column(name = "metric_json", columnDefinition = "TEXT")
    private String metricJson;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;
}
