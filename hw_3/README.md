# ДЗ №3 — Flight Booking: gRPC + Redis

Распределённая система бронирования авиабилетов из двух микросервисов.

## Архитектура

```
Client (REST) - Booking Service - (gRPC) - Flight Service
                      |                          |
                 PostgreSQL               PostgreSQL + Redis Sentinel
```

**Booking Service** — REST API для клиентов, управление бронированиями, хранит данные в своей PostgreSQL.

**Flight Service** — gRPC сервер для управления рейсами и резервациями мест, хранит данные в PostgreSQL, кеширует в Redis.

## Стек

- Java 25, Spring Boot 4.0.3
- Spring gRPC 1.0.2
- PostgreSQL 17, Flyway для миграций
- Redis 7 (Sentinel: master + replica + sentinel)
- Docker Compose
- Lombok, Jackson

## Запуск

```bash
docker-compose up --build
```

Все сервисы поднимаются одной командой. Миграции БД применяются автоматически при старте.

## Эндпоинты Booking Service (порт 8080)

| Метод | URL | Описание |
|-------|-----|----------|
| GET | /flights?origin=SVO&destination=LED&date=2026-04-01 | Поиск рейсов |
| GET | /flights/{id} | Информация о рейсе |
| POST | /bookings | Создание бронирования |
| GET | /bookings/{id} | Получение бронирования |
| POST | /bookings/{id}/cancel | Отмена бронирования |
| GET | /bookings?user_id=X | Список бронирований пользователя |

### Пример создания бронирования

```bash
curl -X POST http://localhost:8080/bookings \
  -H "Content-Type: application/json" \
  -d '{"userId":"user-1","flightId":1,"passengerName":"Ivan Ivanov","passengerEmail":"ivan@test.com","seatCount":2}'
```

## Что реализовано

### Пп. 1-4 — базовая архитектура
- gRPC контракт (`flight_service.proto`) с 4 методами: SearchFlights, GetFlight, ReserveSeats, ReleaseReservation
- enum для статусов, Timestamp для дат, стандартные gRPC error codes (NOT_FOUND, RESOURCE_EXHAUSTED, INVALID_ARGUMENT, UNAUTHENTICATED)
- ER-диаграмма `docs/er-diagram.mermaid`
- 3 таблицы с CHECK constraints, FK, уникальными индексами
- Кодогенерация из .proto через protobuf-maven-plugin
- Межсервисное взаимодействие: Booking Service вызывает Flight Service по gRPC при создании и отмене бронирования
- Флоу создания: GetFlight - ReserveSeats - фиксация цены → запись в БД

### П. 5 — транзакционная целостность
- SELECT FOR UPDATE при резервировании и отмене мест
- Резервирование мест + создание SeatReservation в одной транзакции
- Если gRPC вызов ReserveSeats упал, то бронирование не создаётся

### П. 6 — аутентификация gRPC
- API Key передаётся в gRPC metadata 
- ServerInterceptor с аннотацией @GlobalServerInterceptor проверяет ключ на стороне Flight Service
- ClientInterceptor добавляет ключ на стороне Booking Service
- При невалидном ключе возвращается UNAUTHENTICATED
- Ключ задаётся через переменную окружения GRPC_AUTH_API_KEY

### П. 7 — Redis кеширование
- Стратегия Cache-Aside: проверка кеша - при miss запрос к БД - запись в кеш с TTL
- Кешируется GetFlight (`flight:{id}`, TTL 10 мин) и SearchFlights (`search:{origin}:{dest}:{date}`, TTL 10 мин)
- Инвалидация при мутациях (ReserveSeats, ReleaseReservation)
- Логирование Cache HIT / Cache MISS

### П. 8 — retry
- Максимум 3 попытки с exponential backoff (100ms, 200ms, 400ms)
- Retry только для UNAVAILABLE и DEADLINE_EXCEEDED
- Без retry для INVALID_ARGUMENT, NOT_FOUND, RESOURCE_EXHAUSTED
- Идемпотентность ReserveSeats по booking_id (повторный вызов возвращает существующую резервацию)

### П. 9 — Redis Sentinel
- Redis запущен в отказоустойчивой конфигурации: master + replica + sentinel
- Sentinel мониторит мастер и при его падении автоматически промоутит реплику
- Приложение использует Lettuce клиент с RedisSentinelConfiguration
- При failover приложение автоматически переключается на нового мастера

### П. 10 — Circuit Breaker
- Собственная реализация паттерна Circuit Breaker
- Три состояния: CLOSED - OPEN - HALF_OPEN
- При накоплении ошибок (порог задаётся через CB_FAILURE_THRESHOLD) переходит в OPEN
- В OPEN все запросы сразу возвращают 503 Service Unavailable
- Через заданный таймаут (CB_WAIT_DURATION_SECONDS) переходит в HALF_OPEN и пропускает пробный запрос
- Параметры конфигурируются через переменные окружения
- В логах видны переходы между состояниями

## Структура проекта

```
hw_3/
├── docker-compose.yml
├── flight-service-proto/          # .proto + кодогенерация
│   └── src/main/proto/
│       └── flight_service.proto
├── flight-service/                # gRPC сервер
│   └── src/main/java/org/ilynosov/flight/
│       ├── config/                # Redis, Auth interceptor, Chaos (тестовый)
│       ├── entity/                # JPA сущности
│       ├── grpc/                  # gRPC реализация
│       ├── repository/            # Spring Data JPA
│       └── service/               # бизнес-логика, кеш
├── booking-service/               # REST API + gRPC клиент
│   └── src/main/java/org/ilynosov/booking/
│       ├── client/                # gRPC клиент с retry
│       ├── config/                # Circuit Breaker, Auth interceptor, gRPC config
│       ├── controller/            # REST контроллеры
│       ├── dto/                   # DTO
│       ├── entity/                # JPA сущности
│       ├── repository/            # Spring Data JPA
│       └── service/               # бизнес-логика
└── docs/
    └── er-diagram.mermaid
```