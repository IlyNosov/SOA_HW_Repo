CREATE TABLE bookings (
    id               VARCHAR(36)    PRIMARY KEY,  -- UUID
    user_id          VARCHAR(36)    NOT NULL,
    flight_id        BIGINT         NOT NULL,     -- references flight in Flight Service
    passenger_name   VARCHAR(255)   NOT NULL,
    passenger_email  VARCHAR(255)   NOT NULL,
    seat_count       INT            NOT NULL CHECK (seat_count > 0),
    total_price      DECIMAL(12,2)  NOT NULL CHECK (total_price > 0),
    status           VARCHAR(20)    NOT NULL DEFAULT 'CONFIRMED',
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bookings_user ON bookings (user_id);
CREATE INDEX idx_bookings_flight ON bookings (flight_id);
CREATE INDEX idx_bookings_status ON bookings (status);
