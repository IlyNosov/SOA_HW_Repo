# HW 6 Smart Warehouse

## Cassandra data model

Схема Cassandra лежит в `consumer-service/src/main/resources/cassandra/schema.cql`.
Consumer применяет ее сам при старте через `CassandraMigrationRunner`, поэтому отдельно руками создавать таблицы не нужно.

В первой версии compose используется одна Cassandra-нода, поэтому keyspace создается с `SimpleStrategy` и `replication_factor = 1`.
В части про 8-10 баллов это будет заменено на `NetworkTopologyStrategy` и replication factor 3.

### Таблицы

`inventory_by_product_zone`

Хранит остаток конкретного товара в конкретной зоне.

Запрос:

```sql
SELECT * FROM inventory_by_product_zone WHERE product_id = ? AND zone_id = ?;
```

`product_id` выбран partition key, потому что основной сценарий здесь начинается с товара: найти его остаток в зоне или посмотреть все зоны, где он лежит. `zone_id` выбран clustering key, чтобы внутри partition товара строки были разложены по зонам.

`inventory_by_product`

Хранит общий остаток товара по складу.

Запрос:

```sql
SELECT * FROM inventory_by_product WHERE product_id = ?;
```

Здесь `product_id` это partition key и полный primary key. Таблица специально денормализована, чтобы не считать сумму по всем зонам на чтении.

`inventory_by_zone`

Хранит все товары в конкретной зоне.

Запрос:

```sql
SELECT * FROM inventory_by_zone WHERE zone_id = ?;
```

`zone_id` выбран partition key, потому что этот запрос начинается с зоны. `product_id` выбран clustering key, чтобы в одной зоне можно было хранить много товаров без JOIN.

`processed_events`

Хранит уже обработанные события по `event_id`. Consumer сможет проверять эту таблицу перед обработкой и пропускать дубли. Это нужно для идемпотентности при at-least-once доставке Kafka.

`warehouse_events_history`

Хранит историю событий для аудита. `product_id` это partition key, потому что историю обычно смотрят по товару. `event_timestamp` и `event_id` это clustering keys, чтобы события товара были отсортированы по времени, а одинаковые timestamp не конфликтовали.

`orders_by_id`

Хранит состояние заказа по `order_id`. Для заказа нужен быстрый прямой lookup, поэтому `order_id` это partition key и полный primary key.

## Почему нет JOIN

В Cassandra таблицы проектируются под конкретные запросы. Поэтому одни и те же данные по остаткам лежат в нескольких таблицах:

- по товару и зоне;
- по товару в целом;
- по зоне.

Это нормальная денормализация для Cassandra. Мы платим дополнительными записями при обработке события, зато чтение остается простым и быстрым.

## Producer API и Avro

Producer публикует события в Kafka topic `warehouse-events`.
HTTP endpoint для тестовых событий:

```text
POST http://localhost:8080/api/events
```

Avro-схемы лежат в `producer-service/src/main/resources/avro`:

- `warehouse_event.avsc` - общая схема для всех складских событий;
- `product_received.avsc` - отдельная схема для `PRODUCT_RECEIVED`, чтобы дальше показать schema evolution.

При старте producer регистрирует схемы в Schema Registry:

- subject `warehouse-events-value`;
- subject `product-received-value`.

Поддержанные типы событий:

- `PRODUCT_RECEIVED`;
- `PRODUCT_SHIPPED`;
- `PRODUCT_MOVED`;
- `PRODUCT_RESERVED`;
- `PRODUCT_RELEASED`;
- `INVENTORY_COUNTED`;
- `ORDER_CREATED`;
- `ORDER_COMPLETED`.

Если `event_id` не передан, producer сам создаст UUID. Если `event_timestamp` не передан, producer поставит текущее время.

### Curl-примеры

Приемка товара:

```powershell
curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_type\":\"PRODUCT_RECEIVED\",\"product_id\":\"SKU-001\",\"zone_id\":\"ZONE-A\",\"quantity\":100}"
```

Резервирование товара:

```powershell
curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_type\":\"PRODUCT_RESERVED\",\"product_id\":\"SKU-001\",\"zone_id\":\"ZONE-A\",\"quantity\":30}"
```

Перемещение между зонами:

```powershell
curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_type\":\"PRODUCT_MOVED\",\"product_id\":\"SKU-001\",\"from_zone_id\":\"ZONE-A\",\"to_zone_id\":\"ZONE-B\",\"quantity\":20}"
```

Инвентаризация с явным timestamp:

```powershell
curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_type\":\"INVENTORY_COUNTED\",\"product_id\":\"SKU-001\",\"zone_id\":\"ZONE-A\",\"quantity\":80,\"event_timestamp\":\"2026-05-10T12:00:00Z\"}"
```

Создание заказа:

```powershell
curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_type\":\"ORDER_CREATED\",\"order_id\":\"ORD-001\",\"order_items\":[{\"product_id\":\"SKU-001\",\"zone_id\":\"ZONE-A\",\"quantity\":15}]}"
```

Завершение заказа:

```powershell
curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_type\":\"ORDER_COMPLETED\",\"order_id\":\"ORD-001\"}"
```

## Demo-сценарий для пунктов 1-4

Сначала собрать jar-файлы:

```powershell
mvn -f hw_6\pom.xml -DskipTests package
```

Потом поднять стенд:

```powershell
cd hw_6
docker compose up --build
```

### Базовый цикл склада

1. Приемка товара:

```powershell
curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_id\":\"demo-received-1\",\"event_type\":\"PRODUCT_RECEIVED\",\"product_id\":\"SKU-001\",\"zone_id\":\"ZONE-A\",\"quantity\":100}"
```

Проверка:

```powershell
docker exec hw6-cassandra cqlsh -e "SELECT * FROM warehouse.inventory_by_product_zone WHERE product_id='SKU-001' AND zone_id='ZONE-A';"
docker exec hw6-cassandra cqlsh -e "SELECT * FROM warehouse.inventory_by_product WHERE product_id='SKU-001';"
```

2. Резервирование товара:

```powershell
curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_id\":\"demo-reserved-1\",\"event_type\":\"PRODUCT_RESERVED\",\"product_id\":\"SKU-001\",\"zone_id\":\"ZONE-A\",\"quantity\":30}"
```

Ожидаемо: `available_quantity = 70`, `reserved_quantity = 30`.

3. Перемещение товара:

```powershell
curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_id\":\"demo-moved-1\",\"event_type\":\"PRODUCT_MOVED\",\"product_id\":\"SKU-001\",\"from_zone_id\":\"ZONE-A\",\"to_zone_id\":\"ZONE-B\",\"quantity\":20}"
```

Проверка зоны:

```powershell
docker exec hw6-cassandra cqlsh -e "SELECT * FROM warehouse.inventory_by_zone WHERE zone_id='ZONE-B';"
```

4. Отгрузка товара:

```powershell
curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_id\":\"demo-shipped-1\",\"event_type\":\"PRODUCT_SHIPPED\",\"product_id\":\"SKU-001\",\"zone_id\":\"ZONE-A\",\"quantity\":10}"
```

5. Создание и завершение заказа:

```powershell
curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_id\":\"demo-order-created-1\",\"event_type\":\"ORDER_CREATED\",\"order_id\":\"ORD-001\",\"order_items\":[{\"product_id\":\"SKU-001\",\"zone_id\":\"ZONE-A\",\"quantity\":15}]}"

curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_id\":\"demo-order-completed-1\",\"event_type\":\"ORDER_COMPLETED\",\"order_id\":\"ORD-001\"}"
```

Проверка заказа и истории:

```powershell
docker exec hw6-cassandra cqlsh -e "SELECT * FROM warehouse.orders_by_id WHERE order_id='ORD-001';"
docker exec hw6-cassandra cqlsh -e "SELECT event_id, event_type, source_partition, source_offset FROM warehouse.warehouse_events_history WHERE product_id='SKU-001';"
```

### Идемпотентность

Отправить одно и то же событие два раза с одинаковым `event_id`:

```powershell
curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_id\":\"demo-idempotent-1\",\"event_type\":\"PRODUCT_RECEIVED\",\"product_id\":\"SKU-002\",\"zone_id\":\"ZONE-A\",\"quantity\":50}"

curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_id\":\"demo-idempotent-1\",\"event_type\":\"PRODUCT_RECEIVED\",\"product_id\":\"SKU-002\",\"zone_id\":\"ZONE-A\",\"quantity\":50}"
```

Проверка:

```powershell
docker exec hw6-cassandra cqlsh -e "SELECT * FROM warehouse.inventory_by_product_zone WHERE product_id='SKU-002' AND zone_id='ZONE-A';"
docker exec hw6-cassandra cqlsh -e "SELECT * FROM warehouse.processed_events WHERE event_id='demo-idempotent-1';"
```

Ожидаемо: остаток `SKU-002` остается 50, а не 100. Consumer видит `event_id` в `processed_events`, пропускает дубль и только после этого коммитит offset.

## Demo-сценарии для пунктов 5-7

### Консистентность денормализованных таблиц

Consumer применяет изменения состояния через Cassandra logged batch. В рамках одного события вместе пишутся:

- `inventory_by_product_zone`;
- `inventory_by_zone`;
- `inventory_by_product`;
- `warehouse_events_history`;
- `processed_events`;
- `orders_by_id`, если событие связано с заказом.

Проверка:

```powershell
curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_id\":\"demo-batch-1\",\"event_type\":\"PRODUCT_RECEIVED\",\"product_id\":\"SKU-003\",\"zone_id\":\"ZONE-A\",\"quantity\":100}"

docker exec hw6-cassandra cqlsh -e "SELECT * FROM warehouse.inventory_by_product_zone WHERE product_id='SKU-003' AND zone_id='ZONE-A';"
docker exec hw6-cassandra cqlsh -e "SELECT * FROM warehouse.inventory_by_product WHERE product_id='SKU-003';"
docker exec hw6-cassandra cqlsh -e "SELECT * FROM warehouse.inventory_by_zone WHERE zone_id='ZONE-A';"
docker exec hw6-cassandra cqlsh -e "SELECT * FROM warehouse.processed_events WHERE event_id='demo-batch-1';"
```

Ожидаемо: во всех таблицах есть согласованные значения `available_quantity = 100`.

### Out-of-order события

Consumer сравнивает `event_timestamp` с `last_event_timestamp` в `inventory_by_product_zone`.
Если событие старше уже примененного состояния для `product_id + zone_id`, оно игнорируется, но записывается в history и `processed_events`, чтобы не обрабатываться повторно.

```powershell
curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_id\":\"demo-ordering-1\",\"event_type\":\"PRODUCT_RECEIVED\",\"product_id\":\"SKU-004\",\"zone_id\":\"ZONE-A\",\"quantity\":100,\"event_timestamp\":\"2026-05-10T12:00:00Z\"}"

curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_id\":\"demo-ordering-2\",\"event_type\":\"PRODUCT_SHIPPED\",\"product_id\":\"SKU-004\",\"zone_id\":\"ZONE-A\",\"quantity\":20,\"event_timestamp\":\"2026-05-10T12:05:00Z\"}"

curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_id\":\"demo-ordering-3\",\"event_type\":\"PRODUCT_RECEIVED\",\"product_id\":\"SKU-004\",\"zone_id\":\"ZONE-A\",\"quantity\":50,\"event_timestamp\":\"2026-05-10T12:02:00Z\"}"

docker exec hw6-cassandra cqlsh -e "SELECT * FROM warehouse.inventory_by_product_zone WHERE product_id='SKU-004' AND zone_id='ZONE-A';"
docker exec hw6-cassandra cqlsh -e "SELECT event_id, event_type FROM warehouse.processed_events WHERE event_id='demo-ordering-3';"
```

Ожидаемо: остаток остается `80`, потому что событие `demo-ordering-3` старше последнего примененного timestamp `12:05`.

### DLQ

Для validation/business ошибок consumer не падает и не блокирует partition. Он отправляет исходное событие и причину ошибки в topic `warehouse-events-dlq`, после чего коммитит offset.

Пример невалидного события:

```powershell
curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_id\":\"demo-dlq-1\",\"event_type\":\"PRODUCT_SHIPPED\",\"product_id\":\"SKU-005\",\"zone_id\":\"ZONE-A\",\"quantity\":-5}"
```

Проверка DLQ:

```powershell
docker exec hw6-kafka kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic warehouse-events-dlq --from-beginning --max-messages 1
```

Ожидаемо: в сообщении есть `original_event`, `error_reason`, `error_code`, `failed_at`, `kafka_metadata.partition` и `kafka_metadata.offset`.

После этого можно отправить валидное событие и убедиться, что consumer продолжает работать:

```powershell
curl -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d "{\"event_id\":\"demo-dlq-after-valid-1\",\"event_type\":\"PRODUCT_RECEIVED\",\"product_id\":\"SKU-005\",\"zone_id\":\"ZONE-A\",\"quantity\":10}"

docker exec hw6-cassandra cqlsh -e "SELECT * FROM warehouse.inventory_by_product_zone WHERE product_id='SKU-005' AND zone_id='ZONE-A';"
```
