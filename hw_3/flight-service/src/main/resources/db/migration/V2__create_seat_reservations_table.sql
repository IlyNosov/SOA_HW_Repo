CREATE TABLE seat_reservations (
    id              BIGSERIAL PRIMARY KEY,
    flight_id       BIGINT         NOT NULL REFERENCES flights(id),
    booking_id      VARCHAR(36)    NOT NULL,  -- UUID из Booking Service
    seat_count      INT            NOT NULL CHECK (seat_count > 0),
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_booking_id UNIQUE (booking_id)
);

CREATE INDEX idx_reservations_flight ON seat_reservations (flight_id);
CREATE INDEX idx_reservations_booking ON seat_reservations (booking_id);
CREATE INDEX idx_reservations_status ON seat_reservations (status);
