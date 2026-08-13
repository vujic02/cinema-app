-- =============================================================================
-- Reference / demo seed data.
--
-- Showings are positioned relative to CURDATE() at the moment this migration first
-- runs, which gives roughly five days of content out of the box. Flyway applies a
-- migration once, so the seeded week does not roll forward on its own — recreate the
-- volume (`docker compose down -v`) or add showings through the admin screens once
-- Part 3 lands.
--
-- CURDATE() is the database server's clock; the MySQL container runs UTC, which is
-- also what `spring.jpa.properties.hibernate.jdbc.time_zone` assumes.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Users. Passwords are bcrypt(cost 10):
--   admin@lumen.test    / admin123
--   customer@lumen.test / password123
--   sam@lumen.test      / password123
-- Demo credentials only — nothing here should survive into a real deployment.
-- -----------------------------------------------------------------------------
INSERT INTO users (email, password_hash, full_name, role, created_at)
VALUES ('admin@lumen.test', '$2a$10$pzJVrVkybmtU9cuhTfJ7hOYbqsgOL1buUGBn4uf6.HcY/6sK3j0/C', 'Avery Chen', 'ADMIN', NOW(6)),
       ('customer@lumen.test', '$2a$10$mCTCze6gLoy31mZ7Wz.k2OZAJjPvw7ILtkearvkTfgSl/UikF1.PO', 'Jamie Rivera', 'CUSTOMER', NOW(6)),
       ('sam@lumen.test', '$2a$10$mCTCze6gLoy31mZ7Wz.k2OZAJjPvw7ILtkearvkTfgSl/UikF1.PO', 'Sam Reyes', 'CUSTOMER', NOW(6));

-- -----------------------------------------------------------------------------
-- Venues
-- -----------------------------------------------------------------------------
INSERT INTO venues (name, address, created_at)
VALUES ('Downtown 8', '412 Marlow Street, Central District', NOW(6)),
       ('Riverside IMAX', '9 Quay Parade, Riverside', NOW(6)),
       ('Uptown Cineplex', '1180 Halsey Avenue, Uptown', NOW(6));

-- Downtown 8 uses the reference layout from TECH.md §6:
-- row 1 = 4 seats, rows 2-4 = 6 seats each, row 5 = 7 seats.
INSERT INTO venue_rows (venue_id, row_index, row_label, seat_count)
SELECT v.id, r.row_index, r.row_label, r.seat_count
FROM venues v
         JOIN (SELECT 1 AS row_index, 'A' AS row_label, 4 AS seat_count
               UNION ALL
               SELECT 2, 'B', 6
               UNION ALL
               SELECT 3, 'C', 6
               UNION ALL
               SELECT 4, 'D', 6
               UNION ALL
               SELECT 5, 'E', 7) r
WHERE v.name = 'Downtown 8';

-- The other two venues are larger: 6 rows of 8, matching the design handoff's mock layout.
INSERT INTO venue_rows (venue_id, row_index, row_label, seat_count)
SELECT v.id, r.row_index, r.row_label, 8
FROM venues v
         JOIN (SELECT 1 AS row_index, 'A' AS row_label
               UNION ALL
               SELECT 2, 'B'
               UNION ALL
               SELECT 3, 'C'
               UNION ALL
               SELECT 4, 'D'
               UNION ALL
               SELECT 5, 'E'
               UNION ALL
               SELECT 6, 'F') r
WHERE v.name IN ('Riverside IMAX', 'Uptown Cineplex');

-- Seats are generated from each row's seat_count rather than listed one by one.
-- The aisle sits at the midpoint, so is_aisle_gap marks the seat the gap follows:
-- 4 seats -> after 2, 6 -> after 3, 7 -> after 3, 8 -> after 4.
INSERT INTO seats (row_id, seat_number, seat_type, is_aisle_gap)
SELECT r.id,
       n.n,
       'STANDARD',
       CASE WHEN n.n = FLOOR(r.seat_count / 2) THEN TRUE ELSE FALSE END
FROM venue_rows r
         JOIN (SELECT 1 AS n
               UNION ALL
               SELECT 2
               UNION ALL
               SELECT 3
               UNION ALL
               SELECT 4
               UNION ALL
               SELECT 5
               UNION ALL
               SELECT 6
               UNION ALL
               SELECT 7
               UNION ALL
               SELECT 8) n ON n.n <= r.seat_count;

-- The back row of every venue is premium seating.
UPDATE seats s
    JOIN venue_rows r ON r.id = s.row_id
    JOIN (SELECT venue_id, MAX(row_index) AS last_index FROM venue_rows GROUP BY venue_id) last
    ON last.venue_id = r.venue_id AND last.last_index = r.row_index
SET s.seat_type = 'PREMIUM';

-- -----------------------------------------------------------------------------
-- Movies (titles and hues carried over from the design handoff's mock data)
-- -----------------------------------------------------------------------------
INSERT INTO movies (title, description, duration_minutes, genre, rating, poster_hue, created_at)
VALUES ('Midnight Ember',
        'A night-shift arson investigator starts finding her own handwriting in the case files she has never opened.',
        118, 'Thriller', 'PG-13', 12, NOW(6)),
       ('Salt & Static',
        'Two estranged siblings drive a dying radio station across one last coastal summer.',
        104, 'Drama', 'R', 210, NOW(6)),
       ('Comet Line',
        'The crew of a decommissioned relay station picks up a transmission dated eleven years in the future.',
        132, 'Sci-Fi', 'PG-13', 200, NOW(6)),
       ('Paper Tigers',
        'A failing dojo, a rigged tournament, and four friends who are catastrophically out of practice.',
        97, 'Comedy', 'PG-13', 140, NOW(6)),
       ('The Long Thaw',
        'A glaciologist returns to the village her research is about to erase from the map.',
        121, 'Drama', 'PG', 35, NOW(6));

-- -----------------------------------------------------------------------------
-- Showings. Days 0 and 1 mirror the handoff's "Today" / "Tomorrow" chips exactly;
-- days 2-4 exist so the remaining date filters are not empty.
-- IMAX tickets are priced higher than the other two venues.
-- -----------------------------------------------------------------------------
INSERT INTO showings (movie_id, venue_id, start_time, price, created_at)
SELECT m.id, v.id, s.start_time, s.price, NOW(6)
FROM (SELECT 'Midnight Ember' AS title, 'Downtown 8' AS venue,
             TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 0 DAY), '19:00:00') AS start_time, 14.00 AS price
      UNION ALL
      SELECT 'Midnight Ember', 'Downtown 8', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 0 DAY), '21:45:00'), 14.00
      UNION ALL
      SELECT 'Salt & Static', 'Uptown Cineplex', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 0 DAY), '17:15:00'), 14.00
      UNION ALL
      SELECT 'Comet Line', 'Riverside IMAX', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 0 DAY), '16:00:00'), 18.00
      UNION ALL
      SELECT 'Comet Line', 'Riverside IMAX', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 0 DAY), '19:30:00'), 18.00
      UNION ALL
      SELECT 'Paper Tigers', 'Uptown Cineplex', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 0 DAY), '17:45:00'), 14.00

      UNION ALL
      SELECT 'Midnight Ember', 'Riverside IMAX', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '18:30:00'), 18.00
      UNION ALL
      SELECT 'Salt & Static', 'Downtown 8', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '20:00:00'), 14.00
      UNION ALL
      SELECT 'Comet Line', 'Uptown Cineplex', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '21:00:00'), 14.00
      UNION ALL
      SELECT 'Paper Tigers', 'Downtown 8', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '19:15:00'), 14.00
      UNION ALL
      SELECT 'The Long Thaw', 'Downtown 8', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '18:00:00'), 14.00
      UNION ALL
      SELECT 'The Long Thaw', 'Riverside IMAX', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '20:45:00'), 18.00

      UNION ALL
      SELECT 'Comet Line', 'Downtown 8', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '19:00:00'), 14.00
      UNION ALL
      SELECT 'The Long Thaw', 'Uptown Cineplex', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '18:30:00'), 14.00
      UNION ALL
      SELECT 'Midnight Ember', 'Riverside IMAX', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '21:00:00'), 18.00

      UNION ALL
      SELECT 'Paper Tigers', 'Riverside IMAX', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '17:30:00'), 18.00
      UNION ALL
      SELECT 'Salt & Static', 'Downtown 8', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '20:15:00'), 14.00
      UNION ALL
      SELECT 'Comet Line', 'Uptown Cineplex', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '19:45:00'), 14.00

      UNION ALL
      SELECT 'The Long Thaw', 'Downtown 8', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 4 DAY), '18:45:00'), 14.00
      UNION ALL
      SELECT 'Midnight Ember', 'Uptown Cineplex', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 4 DAY), '20:30:00'), 14.00
      UNION ALL
      SELECT 'Comet Line', 'Riverside IMAX', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 4 DAY), '16:15:00'), 18.00) s
         JOIN movies m ON m.title = s.title
         JOIN venues v ON v.name = s.venue;

-- -----------------------------------------------------------------------------
-- Pre-sold seats, so the seat map has something taken on first load and My Bookings
-- is not empty. Sam owns the scattered sold seats; Jamie owns the demo login's booking.
-- -----------------------------------------------------------------------------
INSERT INTO bookings (booking_reference, showing_id, seat_id, user_id, status, price_paid, created_at)
SELECT b.booking_ref, sh.id, st.id, u.id, 'SOLD', sh.price, NOW(6)
FROM (SELECT 'LUM-40218' AS booking_ref, 'sam@lumen.test' AS email, 'Downtown 8' AS venue,
             TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 0 DAY), '19:00:00') AS start_time,
             'A' AS row_label, 2 AS seat_number
      UNION ALL
      SELECT 'LUM-40218', 'sam@lumen.test', 'Downtown 8', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 0 DAY), '19:00:00'), 'A', 3
      UNION ALL
      SELECT 'LUM-40218', 'sam@lumen.test', 'Downtown 8', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 0 DAY), '19:00:00'), 'B', 5
      UNION ALL
      SELECT 'LUM-40218', 'sam@lumen.test', 'Downtown 8', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 0 DAY), '19:00:00'), 'C', 1
      UNION ALL
      SELECT 'LUM-40218', 'sam@lumen.test', 'Downtown 8', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 0 DAY), '19:00:00'), 'C', 6
      UNION ALL
      SELECT 'LUM-40218', 'sam@lumen.test', 'Downtown 8', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 0 DAY), '19:00:00'), 'D', 4
      UNION ALL
      SELECT 'LUM-40218', 'sam@lumen.test', 'Downtown 8', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 0 DAY), '19:00:00'), 'D', 5
      UNION ALL
      SELECT 'LUM-40218', 'sam@lumen.test', 'Downtown 8', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 0 DAY), '19:00:00'), 'E', 2
      UNION ALL
      SELECT 'LUM-40218', 'sam@lumen.test', 'Downtown 8', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 0 DAY), '19:00:00'), 'E', 6
      UNION ALL
      SELECT 'LUM-77291', 'customer@lumen.test', 'Riverside IMAX', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 0 DAY), '19:30:00'), 'C', 4
      UNION ALL
      SELECT 'LUM-77291', 'customer@lumen.test', 'Riverside IMAX', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 0 DAY), '19:30:00'), 'C', 5) b
         JOIN users u ON u.email = b.email
         JOIN venues v ON v.name = b.venue
         JOIN showings sh ON sh.venue_id = v.id AND sh.start_time = b.start_time
         JOIN venue_rows r ON r.venue_id = v.id AND r.row_label = b.row_label
         JOIN seats st ON st.row_id = r.id AND st.seat_number = b.seat_number;
