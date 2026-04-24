package org.ilynosov.hw5.producer;

import com.google.protobuf.Message;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.ilynosov.hw5.producer.proto.MovieEventProto.DeviceType;
import org.ilynosov.hw5.producer.proto.MovieEventProto.EventType;
import org.ilynosov.hw5.producer.proto.MovieEventProto.MovieEvent;
import org.ilynosov.hw5.producer.serializer.ProtobufSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PipelineIntegrationTest {

    static final Network NETWORK = Network.newNetwork();

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    )
        .withNetwork(NETWORK)
        .withNetworkAliases("kafka-test");

    @Container
    static final GenericContainer<?> CLICKHOUSE = new GenericContainer<>(
        DockerImageName.parse("clickhouse/clickhouse-server:24.1")
    )
        .withNetwork(NETWORK)
        .withNetworkAliases("clickhouse-test")
        .withExposedPorts(8123)
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("movie_event.proto"),
            "/var/lib/clickhouse/format_schemas/movie_event.proto"
        )
        .waitingFor(Wait.forHttp("/ping").forPort(8123).withStartupTimeout(Duration.ofSeconds(60)));

    @BeforeAll
    static void setup() throws Exception {
        createTopic();
        initClickHouseSchema();
    }

    @Test
    void eventPublishedToKafkaAppearsInClickHouse() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String userId = "test-user-" + UUID.randomUUID();

        MovieEvent event = MovieEvent.newBuilder()
            .setEventId(eventId)
            .setUserId(userId)
            .setMovieId("movie-test-001")
            .setEventType(EventType.VIEW_STARTED)
            .setTimestamp(Instant.now().toEpochMilli())
            .setDeviceType(DeviceType.DESKTOP)
            .setSessionId(UUID.randomUUID().toString())
            .setProgressSeconds(0)
            .build();

        KafkaTemplate<String, Message> kafkaTemplate = buildKafkaTemplate();
        kafkaTemplate.send("movie-events", userId, event).get(10, TimeUnit.SECONDS);

        Awaitility.await()
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(2, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(countEvents(eventId)).isGreaterThan(0));

        Map<String, String> row = fetchEvent(eventId);
        assertThat(row.get("event_id")).isEqualTo(eventId);
        assertThat(row.get("user_id")).isEqualTo(userId);
        assertThat(row.get("movie_id")).isEqualTo("movie-test-001");
        assertThat(row.get("event_type")).isEqualTo("VIEW_STARTED");
        assertThat(row.get("device_type")).isEqualTo("DESKTOP");
    }

    private static void createTopic() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
            "bootstrap.servers", KAFKA.getBootstrapServers()
        ))) {
            admin.createTopics(List.of(new NewTopic("movie-events", 3, (short) 1))).all().get();
        }
    }

    private static void initClickHouseSchema() {
        String kafkaInternalBroker = "kafka-test:9092";
        execute("""
            CREATE TABLE IF NOT EXISTS movie_events_kafka (
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
                kafka_broker_list = '%s',
                kafka_topic_list  = 'movie-events',
                kafka_group_name  = 'ch-test',
                kafka_format      = 'ProtobufSingle',
                kafka_schema      = 'movie_event:MovieEvent'
            """.formatted(kafkaInternalBroker));

        execute("""
            CREATE TABLE IF NOT EXISTS movie_events (
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
            """);

        execute("""
            CREATE MATERIALIZED VIEW IF NOT EXISTS movie_events_mv TO movie_events AS
            SELECT
                event_id, user_id, movie_id, event_type,
                toDateTime(intDiv(timestamp, 1000)) AS timestamp,
                device_type, session_id, progress_seconds
            FROM movie_events_kafka
            """);
    }

    private static String chBaseUrl() {
        return "http://" + CLICKHOUSE.getHost() + ":" + CLICKHOUSE.getMappedPort(8123);
    }

    private static void execute(String sql) {
        new RestTemplate().postForObject(chBaseUrl(), sql, String.class);
    }

    private int countEvents(String eventId) {
        String result = new RestTemplate().postForObject(
            chBaseUrl() + "?default_format=TabSeparated",
            "SELECT count() FROM movie_events WHERE event_id = '" + eventId + "'",
            String.class
        );
        return result == null || result.isBlank() ? 0 : Integer.parseInt(result.trim());
    }

    private Map<String, String> fetchEvent(String eventId) {
        String result = new RestTemplate().postForObject(
            chBaseUrl() + "?default_format=TabSeparated",
            "SELECT event_id, user_id, movie_id, event_type, device_type " +
            "FROM movie_events WHERE event_id = '" + eventId + "' LIMIT 1",
            String.class
        );
        if (result == null || result.isBlank()) return Map.of();
        String[] parts = result.trim().split("\t");
        return Map.of(
            "event_id", parts[0],
            "user_id", parts[1],
            "movie_id", parts[2],
            "event_type", parts[3],
            "device_type", parts[4]
        );
    }

    private KafkaTemplate<String, Message> buildKafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ProtobufSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }
}
