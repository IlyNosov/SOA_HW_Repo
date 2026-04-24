package org.ilynosov.hw5.aggregation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ilynosov.hw5.aggregation.clickhouse.ClickHouseHttpClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    private final ClickHouseHttpClient clickHouse;

    public void computeAndStoreFullCohort(LocalDate from, LocalDate to) {
        log.info("Computing retention cohort heatmap from {} to {}", from, to);

        List<Map<String, Object>> rows = clickHouse.queryForList(
            "WITH first_views AS (" +
            "  SELECT user_id, toDate(min(timestamp)) AS first_date" +
            "  FROM movie_events WHERE event_type = 'VIEW_STARTED'" +
            "  GROUP BY user_id" +
            ")," +
            "cohort_sizes AS (" +
            "  SELECT first_date, uniq(user_id) AS cohort_size FROM first_views" +
            "  WHERE first_date BETWEEN toDate('" + from + "') AND toDate('" + to + "')" +
            "  GROUP BY first_date" +
            ")," +
            "activity AS (" +
            "  SELECT fv.user_id, fv.first_date," +
            "    dateDiff('day', fv.first_date, toDate(me.timestamp)) AS day_number" +
            "  FROM first_views fv" +
            "  INNER JOIN movie_events me ON fv.user_id = me.user_id" +
            "  WHERE fv.first_date BETWEEN toDate('" + from + "') AND toDate('" + to + "')" +
            "    AND day_number BETWEEN 0 AND 7" +
            "  GROUP BY fv.user_id, fv.first_date, day_number" +
            ")" +
            "SELECT" +
            "  a.first_date AS cohort_date, a.day_number, cs.cohort_size," +
            "  uniq(a.user_id) AS retained_users," +
            "  retained_users / if(cs.cohort_size > 0, cs.cohort_size, 1) AS retention_pct" +
            " FROM activity a" +
            " INNER JOIN cohort_sizes cs ON a.first_date = cs.first_date" +
            " GROUP BY cohort_date, day_number, cs.cohort_size" +
            " ORDER BY cohort_date, day_number");

        if (rows.isEmpty()) {
            log.info("No cohort data for period {} - {}", from, to);
            return;
        }

        StringBuilder sb = new StringBuilder(
            "INSERT INTO retention_cohorts (cohort_date, day_number, cohort_size, retained_users, retention_pct) VALUES ");
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            if (i > 0) sb.append(",");
            sb.append("('").append(row.get("cohort_date")).append("',")
              .append(row.get("day_number")).append(",")
              .append(row.get("cohort_size")).append(",")
              .append(row.get("retained_users")).append(",")
              .append(row.get("retention_pct")).append(")");
        }
        clickHouse.execute(sb.toString());

        log.info("Stored {} cohort rows", rows.size());
    }
}
