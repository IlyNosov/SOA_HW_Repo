CREATE TABLE flights (
    id                BIGSERIAL PRIMARY KEY,
    flight_number     VARCHAR(10)    NOT NULL,
    airline           VARCHAR(100)   NOT NULL,
    origin            VARCHAR(3)     NOT NULL,
    destination       VARCHAR(3)     NOT NULL,
    departure_time    TIMESTAMP      NOT NULL,
    arrival_time      TIMESTAMP      NOT NULL,
    total_seats       INT            NOT NULL CHECK (total_seats > 0),
    available_seats   INT            NOT NULL CHECK (available_seats >= 0),
    price             DECIMAL(10,2)  NOT NULL CHECK (price > 0),
    status            VARCHAR(20)    NOT NULL DEFAULT 'SCHEDULED',

    CONSTRAINT uq_flight_number_date UNIQUE (flight_number, departure_time),
    CONSTRAINT chk_available_le_total CHECK (available_seats <= total_seats)
);

CREATE INDEX idx_flights_route_date ON flights (origin, destination, departure_time);
CREATE INDEX idx_flights_status ON flights (status);
