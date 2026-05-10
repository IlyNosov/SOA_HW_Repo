CREATE KEYSPACE IF NOT EXISTS ${keyspace}
WITH replication = {
    'class': 'SimpleStrategy',
    'replication_factor': 1
};

CREATE TABLE IF NOT EXISTS ${keyspace}.inventory_by_product_zone (
    product_id text,
    zone_id text,
    available_quantity int,
    reserved_quantity int,
    last_event_timestamp timestamp,
    updated_at timestamp,
    supplier_id text,
    PRIMARY KEY ((product_id), zone_id)
);

CREATE TABLE IF NOT EXISTS ${keyspace}.inventory_by_product (
    product_id text,
    total_available_quantity int,
    total_reserved_quantity int,
    last_event_timestamp timestamp,
    updated_at timestamp,
    PRIMARY KEY (product_id)
);

CREATE TABLE IF NOT EXISTS ${keyspace}.inventory_by_zone (
    zone_id text,
    product_id text,
    available_quantity int,
    reserved_quantity int,
    last_event_timestamp timestamp,
    updated_at timestamp,
    supplier_id text,
    PRIMARY KEY ((zone_id), product_id)
);

CREATE TABLE IF NOT EXISTS ${keyspace}.processed_events (
    event_id text,
    event_type text,
    processed_at timestamp,
    source_partition int,
    source_offset bigint,
    PRIMARY KEY (event_id)
);

CREATE TABLE IF NOT EXISTS ${keyspace}.warehouse_events_history (
    product_id text,
    event_timestamp timestamp,
    event_id text,
    event_type text,
    zone_id text,
    payload text,
    source_partition int,
    source_offset bigint,
    PRIMARY KEY ((product_id), event_timestamp, event_id)
) WITH CLUSTERING ORDER BY (event_timestamp DESC, event_id ASC);

CREATE TABLE IF NOT EXISTS ${keyspace}.orders_by_id (
    order_id text,
    status text,
    items_json text,
    created_at timestamp,
    updated_at timestamp,
    last_event_timestamp timestamp,
    PRIMARY KEY (order_id)
);
