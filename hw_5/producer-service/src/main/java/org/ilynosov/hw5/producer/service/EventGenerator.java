package org.ilynosov.hw5.producer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ilynosov.hw5.producer.proto.MovieEventProto.DeviceType;
import org.ilynosov.hw5.producer.proto.MovieEventProto.EventType;
import org.ilynosov.hw5.producer.proto.MovieEventProto.MovieEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "generator.enabled", havingValue = "true", matchIfMissing = true)
public class EventGenerator {

    private static final List<String> USER_IDS = List.of(
        "user-001", "user-002", "user-003", "user-004", "user-005",
        "user-006", "user-007", "user-008", "user-009", "user-010"
    );
    private static final List<String> MOVIE_IDS = List.of(
        "movie-001", "movie-002", "movie-003", "movie-004", "movie-005"
    );
    private static final List<DeviceType> DEVICES = List.of(
        DeviceType.MOBILE, DeviceType.DESKTOP, DeviceType.TV, DeviceType.TABLET
    );

    private final EventProducer producer;
    private final Random random = new Random();

    private record SessionState(String userId, String movieId, DeviceType device, int progress, boolean paused) {}

    private final Map<String, SessionState> activeSessions = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${generator.interval-ms:3000}")
    public void generate() {
        int action = random.nextInt(10);

        if (action < 3 || activeSessions.isEmpty()) {
            startNewSession();
        } else if (action < 5) {
            advanceSession();
        } else if (action < 7) {
            pauseOrResumeSession();
        } else if (action < 9) {
            finishSession();
        } else {
            publishSearch();
        }
    }

    private void startNewSession() {
        String userId = USER_IDS.get(random.nextInt(USER_IDS.size()));
        String movieId = MOVIE_IDS.get(random.nextInt(MOVIE_IDS.size()));
        String sessionId = UUID.randomUUID().toString();
        DeviceType device = DEVICES.get(random.nextInt(DEVICES.size()));

        activeSessions.put(sessionId, new SessionState(userId, movieId, device, 0, false));

        publish(userId, movieId, sessionId, device, EventType.VIEW_STARTED, 0);
    }

    private void advanceSession() {
        activeSessions.entrySet().stream()
            .filter(e -> !e.getValue().paused())
            .findFirst()
            .ifPresent(entry -> {
                SessionState s = entry.getValue();
                int newProgress = s.progress() + 30 + random.nextInt(60);
                activeSessions.put(entry.getKey(), new SessionState(
                    s.userId(), s.movieId(), s.device(), newProgress, false));

                if (random.nextInt(5) == 0) {
                    publish(s.userId(), s.movieId(), entry.getKey(), s.device(), EventType.LIKED, newProgress);
                }
            });
    }

    private void pauseOrResumeSession() {
        activeSessions.entrySet().stream().findFirst().ifPresent(entry -> {
            SessionState s = entry.getValue();
            if (s.paused()) {
                activeSessions.put(entry.getKey(), new SessionState(
                    s.userId(), s.movieId(), s.device(), s.progress(), false));
                publish(s.userId(), s.movieId(), entry.getKey(), s.device(), EventType.VIEW_RESUMED, s.progress());
            } else {
                activeSessions.put(entry.getKey(), new SessionState(
                    s.userId(), s.movieId(), s.device(), s.progress(), true));
                publish(s.userId(), s.movieId(), entry.getKey(), s.device(), EventType.VIEW_PAUSED, s.progress());
            }
        });
    }

    private void finishSession() {
        activeSessions.entrySet().stream().findFirst().ifPresent(entry -> {
            SessionState s = entry.getValue();
            int finalProgress = s.progress() + 60 + random.nextInt(120);
            publish(s.userId(), s.movieId(), entry.getKey(), s.device(), EventType.VIEW_FINISHED, finalProgress);
            activeSessions.remove(entry.getKey());
        });
    }

    private void publishSearch() {
        String userId = USER_IDS.get(random.nextInt(USER_IDS.size()));
        String sessionId = UUID.randomUUID().toString();
        DeviceType device = DEVICES.get(random.nextInt(DEVICES.size()));
        publish(userId, "search", sessionId, device, EventType.SEARCHED, 0);
    }

    private void publish(String userId, String movieId, String sessionId,
                         DeviceType device, EventType eventType, int progress) {
        MovieEvent event = MovieEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setUserId(userId)
            .setMovieId(movieId)
            .setEventType(eventType)
            .setTimestamp(Instant.now().toEpochMilli())
            .setDeviceType(device)
            .setSessionId(sessionId)
            .setProgressSeconds(progress)
            .build();
        producer.publish(event);
    }
}
