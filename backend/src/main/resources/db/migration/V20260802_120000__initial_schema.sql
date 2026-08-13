-- =============================================================================
-- Cinema Seat Booking System — initial schema
--
-- Flyway owns this schema outright. Hibernate runs with ddl-auto=validate and is
-- never allowed to create or alter anything (TECH.md §3a).
--
-- Tables follow TECH.md §6 with three deliberate deviations, each noted inline:
--   1. `rows`       -> `venue_rows`  (ROWS is a reserved word in MySQL 8)
--   2. `row_number` -> `row_index` + `row_label` (ROW_NUMBER is reserved; and the
--      UI needs a display label like "A" separately from the ordering key)
--   3. `bookings` gains `booking_reference` and `price_paid` — TECH.md models one
--      row per seat, but a customer buys several seats as one purchase, so the
--      rows need a shared key and a price snapshot.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Auth
-- -----------------------------------------------------------------------------
CREATE TABLE users
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL, -- bcrypt output is 60 chars
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'CUSTOMER'))
) ENGINE = InnoDB;

-- Stateless JWT can't be revoked before expiry, so refresh tokens get their own
-- table instead of a server-side session store (TECH.md §3a). Only the SHA-256 of
-- the token is stored — a database leak must not hand out usable tokens.
CREATE TABLE refresh_tokens
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_refresh_tokens_user (user_id)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- Venues and seat layout
-- -----------------------------------------------------------------------------
CREATE TABLE venues
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(120) NOT NULL,
    address    VARCHAR(255) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_venues_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE venue_rows
(
    id         BIGINT     NOT NULL AUTO_INCREMENT,
    venue_id   BIGINT     NOT NULL,
    row_index  INT        NOT NULL, -- 1-based ordering, screen-nearest first
    row_label  VARCHAR(4) NOT NULL, -- what the customer sees: "A", "B", ...
    seat_count INT        NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_venue_rows_venue_index UNIQUE (venue_id, row_index),
    CONSTRAINT uq_venue_rows_venue_label UNIQUE (venue_id, row_label),
    CONSTRAINT fk_venue_rows_venue FOREIGN KEY (venue_id) REFERENCES venues (id) ON DELETE CASCADE,
    CONSTRAINT ck_venue_rows_seat_count CHECK (seat_count BETWEEN 1 AND 40)
) ENGINE = InnoDB;

CREATE TABLE seats
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    row_id       BIGINT      NOT NULL,
    seat_number  INT         NOT NULL,
    seat_type    VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    -- TRUE means "render an aisle gap AFTER this seat" (TECH.md §6).
    is_aisle_gap BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uq_seats_row_number UNIQUE (row_id, seat_number),
    CONSTRAINT fk_seats_row FOREIGN KEY (row_id) REFERENCES venue_rows (id) ON DELETE CASCADE,
    CONSTRAINT ck_seats_type CHECK (seat_type IN ('STANDARD', 'PREMIUM', 'ACCESSIBLE'))
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- Catalogue
-- -----------------------------------------------------------------------------
CREATE TABLE movies
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    title            VARCHAR(200) NOT NULL,
    description      VARCHAR(2000),
    duration_minutes INT          NOT NULL,
    genre            VARCHAR(60)  NOT NULL,
    rating           VARCHAR(10)  NOT NULL,
    -- Drives the placeholder poster gradient in the UI until real artwork exists.
    poster_hue       INT          NOT NULL DEFAULT 200,
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_movies_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_movies_hue CHECK (poster_hue BETWEEN 0 AND 359),
    INDEX idx_movies_title (title)
) ENGINE = InnoDB;

CREATE TABLE showings
(
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    movie_id   BIGINT        NOT NULL,
    venue_id   BIGINT        NOT NULL,
    start_time DATETIME(6)   NOT NULL,
    price      DECIMAL(10, 2) NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    -- A venue is one auditorium with one seat layout in this model, so it cannot
    -- run two showings at the same instant.
    CONSTRAINT uq_showings_venue_start UNIQUE (venue_id, start_time),
    CONSTRAINT fk_showings_movie FOREIGN KEY (movie_id) REFERENCES movies (id),
    CONSTRAINT fk_showings_venue FOREIGN KEY (venue_id) REFERENCES venues (id),
    CONSTRAINT ck_showings_price CHECK (price >= 0),
    INDEX idx_showings_start_time (start_time),
    INDEX idx_showings_movie (movie_id)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- Bookings
-- -----------------------------------------------------------------------------
CREATE TABLE bookings
(
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    -- Shared by every seat bought in one purchase; this is the "LUM-77291" the
    -- customer sees on their ticket.
    booking_reference VARCHAR(20)    NOT NULL,
    showing_id        BIGINT         NOT NULL,
    seat_id           BIGINT         NOT NULL,
    user_id           BIGINT         NOT NULL,
    status            VARCHAR(10)    NOT NULL,
    -- Snapshot of showings.price at purchase time, so later price edits don't
    -- rewrite history.
    price_paid        DECIMAL(10, 2) NOT NULL,
    created_at        DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    -- The backstop for the seat race (TECH.md §5). Redis NX is what normally
    -- decides the winner; this guarantees that even with Redis down, or two
    -- transactions slipping past the hold check, only one row can exist per seat.
    CONSTRAINT uq_bookings_showing_seat UNIQUE (showing_id, seat_id),
    CONSTRAINT fk_bookings_showing FOREIGN KEY (showing_id) REFERENCES showings (id),
    CONSTRAINT fk_bookings_seat FOREIGN KEY (seat_id) REFERENCES seats (id),
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_bookings_status CHECK (status IN ('HELD', 'SOLD')),
    INDEX idx_bookings_reference (booking_reference),
    INDEX idx_bookings_user_created (user_id, created_at DESC),
    INDEX idx_bookings_showing_status (showing_id, status)
) ENGINE = InnoDB;
