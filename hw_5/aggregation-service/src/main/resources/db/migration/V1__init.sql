CREATE TABLE IF NOT EXISTS daily_metrics
(
    id          BIGSERIAL PRIMARY KEY,
    date        DATE         NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    metric_value DOUBLE PRECISION,
    metric_json  TEXT,
    computed_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_daily_metrics_date_name UNIQUE (date, metric_name)
);
