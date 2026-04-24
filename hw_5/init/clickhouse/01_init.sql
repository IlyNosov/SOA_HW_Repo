CREATE TABLE IF NOT EXISTS movie_events_kafka
(
    event_id         String,
    user_id          String,
    movie_id         String,
    event_type       String,
    timestamp        Int64,
    device_type      String,
    session_id       String,
    progress_seconds Int32
) ENGINE = Kafka()
SETTINGS
    kafka_broker_list       = 'kafka1:29092,kafka2:29092',
    kafka_topic_list        = 'movie-events',
    kafka_group_name        = 'clickhouse-consumer',
    kafka_format            = 'ProtobufSingle',
    kafka_schema            = 'movie_event:MovieEvent',
    kafka_num_consumers     = 1;

CREATE TABLE IF NOT EXISTS movie_events
(
    event_id         String,
    user_id          String,
    movie_id         String,
    event_type       LowCardinality(String),
    timestamp        DateTime,
    device_type      LowCardinality(String),
    session_id       String,
    progress_seconds Int32,
    date             Date DEFAULT toDate(timestamp)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (user_id, timestamp)
SETTINGS index_granularity = 8192;

CREATE MATERIALIZED VIEW IF NOT EXISTS movie_events_mv TO movie_events AS
SELECT
    event_id,
    user_id,
    movie_id,
    event_type,
    toDateTime(intDiv(timestamp, 1000)) AS timestamp,
    device_type,
    session_id,
    progress_seconds
FROM movie_events_kafka;

CREATE TABLE IF NOT EXISTS daily_aggregates
(
    date         Date,
    metric_name  LowCardinality(String),
    metric_value Float64,
    metric_json  String      DEFAULT '',
    computed_at  DateTime    DEFAULT now()
) ENGINE = ReplacingMergeTree(computed_at)
ORDER BY (date, metric_name);

CREATE TABLE IF NOT EXISTS retention_cohorts
(
    cohort_date    Date,
    day_number     UInt8,
    cohort_size    UInt64,
    retained_users UInt64,
    retention_pct  Float64,
    computed_at    DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(computed_at)
ORDER BY (cohort_date, day_number);
