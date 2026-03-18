package org.ilynosov.flight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

// сервис для кеширования данных в Redis по стратегии Cache-Aside
@Service
@RequiredArgsConstructor
@Slf4j
public class FlightCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    // получить значение из кеша
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                log.info("Cache HIT: {}", key);
                return Optional.of(objectMapper.readValue(json, type));
            }
            log.info("Cache MISS: {}", key);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Ошибка чтения из кеша для ключа {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    // положить значение в кеш с TTL
    public void put(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL);
            log.debug("Cache PUT: {} (TTL={})", key, CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Ошибка записи в кеш для ключа {}: {}", key, e.getMessage());
        }
    }

    // удалить конкретный ключ
    public void evict(String key) {
        redisTemplate.delete(key);
        log.debug("Cache EVICT: {}", key);
    }

    // удалить все ключи по префиксу
    public void evictByPrefix(String prefix) {
        var keys = redisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("Cache EVICT по префиксу: {} ({} ключей)", prefix, keys.size());
        }
    }
}