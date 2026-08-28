# Cinema Seat Booking — Execution Plan

Source of truth: `TECH.md`. UI source: the design handoff, which lives at `/frontend` (customer
flow only — admin screens do not exist yet and get designed from the same Tailwind tokens,
now defined as real theme tokens in `frontend/tailwind.config.ts`).

**Decisions locked:**
- Build order: **backend-first**. Seat-hold concurrency is the flagship feature; the API contract
  gets settled before the UI is wired to it, so the frontend is written once.
- Admin scope: **full per TECH.md §2** — venues, seat layouts, movies, showings, all-bookings,
  analytics.
- Build tool: **Maven** (wrapper committed), **Java 21**, **Spring Boot 3.4.x**.
- Target layout: `/backend`, `/frontend`, `/docker-compose.yml`, `/.github/workflows` (TECH.md §8).

---

## Part 1 — Repo scaffold, infra, database schema ✅ DONE

Foundation everything else sits on. Nothing to demo except a booting app and a migrated DB.

- [x] `/backend` Maven project: Spring Boot 3.4.1, Java 21, Maven wrapper (`mvnw`)
- [x] Dependencies: web, data-jpa, validation, security, websocket, data-redis, flyway
      (+ flyway-mysql), mysql-connector-j, springdoc-openapi, jjwt, lombok, testcontainers
- [x] `docker-compose.yml` — `mysql:8.4` + `redis:7` with healthchecks and named volumes.
      MySQL on host port **3307** to avoid clashing with a local install. Redis starts with
      `--notify-keyspace-events Ex` so Part 4 can listen for hold expiry.
- [x] `application.yml` + `application-local.yml`: `ddl-auto=validate`, `open-in-view=false`,
      HikariCP, server-side prepared-statement caching, Flyway on `classpath:db/migration`
- [x] `V20260802_120000__initial_schema.sql` — TECH.md §6 tables: `users`, `refresh_tokens`,
      `venues`, `venue_rows`, `seats`, `movies`, `showings`, `bookings`
      - `bookings` has `UNIQUE (showing_id, seat_id)` — the MySQL backstop for the seat race
      - `seats` has `is_aisle_gap` per TECH.md §6
- [x] `V20260802_120100__seed_reference_data.sql` — 3 venues, 5 movies, 21 showings over 5 days,
      3 users, 11 pre-sold seats
- [x] JPA entities + base repositories for every table (derived queries only at this stage)
- [x] Package-by-feature skeleton: `auth`, `venue`, `movie`, `showing`, `booking`, `config`,
      `common`
- [x] Global exception handler + consistent `ApiError` response shape
- [x] **Verified:** containers healthy, app boots on the `local` profile, both migrations
      applied (`flyway_schema_history` success=1), Hibernate `validate` passed against the
      Flyway schema, `/actuator/health` UP with db + redis components, Swagger UI 200.
      Seeded: 3 venues / 17 rows / 125 seats / 5 movies / 21 showings / 11 bookings, with
      Downtown 8 at exactly the TECH.md §6 reference layout (29 seats: 4/6/6/6/7).
      Seat-race backstop proven by hand: a duplicate insert fails with
      `ERROR 1062 ... Duplicate entry '1-3' for key 'bookings.uq_bookings_showing_seat'`.

### Part 1 deviations from TECH.md (all deliberate, all commented in the SQL)

- Migrations are **timestamp-named** per TECH.md §3a (`V20260802_120000__…`), not `V1__…`.
- `rows` → `venue_rows`, `row_number` → `row_index` + `row_label`: both original names are
  reserved words in MySQL 8, and the UI needs a display label ("A") separate from the sort key.
- `bookings` gains `booking_reference` (one purchase spans several seat rows) and `price_paid`
  (price snapshot, so editing `showings.price` later doesn't rewrite history).
- `movies` gains `genre`, `rating`, `poster_hue` — all three are rendered by the handoff's
  movie cards and had nowhere to live in the §6 sketch.
- `venues` are modelled as one auditorium each (one layout per venue), enforced by
  `UNIQUE (venue_id, start_time)` on showings.

### How to run what exists now

```
docker compose up -d                                   # mysql (3307) + redis (6379)
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# http://localhost:8080/actuator/health
# http://localhost:8080/swagger-ui.html
```

Seeded logins (demo only): `admin@lumen.test` / `admin123`,
`customer@lumen.test` / `password123`, `sam@lumen.test` / `password123`.

Seat holds land in Redis, so they are inspectable by hand while the app runs:

```
docker exec -it cinema-redis redis-cli --scan --pattern 'hold:*'
docker exec -it cinema-redis redis-cli ttl hold:1:3
```

## Part 2 — Auth (JWT + refresh tokens) ✅ DONE

Part 1 left more auth scaffolding than this list originally implied: the entities, repositories
and config keys all existed and were Flyway-validated. Part 2 added everything that *issues* and
*checks* a token.

### Already in place (landed with Part 1)

- [x] `User` entity with `ADMIN` / `CUSTOMER` roles, BCrypt hashing
      - `auth/domain/User.java` + `Role`, with `@Builder` and `@CreatedDate`. Note `full_name`
        is NOT NULL — the register DTO has to carry it.
      - `PasswordEncoder` bean is a plain `BCryptPasswordEncoder` (`config/SecurityConfig.java`).
        Keep it that way: the seeded hashes are raw `$2a$10$…` with no `{bcrypt}` prefix, so
        swapping in a `DelegatingPasswordEncoder` fails every seeded login with
        `There is no PasswordEncoder mapped for the id "null"`.
- [x] `RefreshTokenRepository` exactly as specified in TECH.md §3a
      - plus `deleteExpiredBefore(cutoff)` for housekeeping
      - caveat for the service layer: it filters on `revoked` only — **expiry is not in the
        query** and has to be checked in code
- [x] `RefreshToken` entity + `refresh_tokens` table — `token_hash VARCHAR(64)` (SHA-256 hex),
      `expires_at`, `revoked`, `ON DELETE CASCADE` to `users`
- [x] `UserRepository.findByEmailIgnoreCase` / `existsByEmailIgnoreCase`
- [x] JWT config bound and typed: `AppProperties.Jwt(issuer, accessTokenTtlMinutes=15,
      refreshTokenTtlDays=14, secret)`, secret overridable via the `JWT_SECRET` env var
- [x] jjwt **0.12.6** on the classpath — 0.12 API (`Jwts.parser().verifyWith(…)`), not the 0.11
      `parserBuilder()` / `setSubject()` API that most examples still show
- [x] `ApiException` / `ApiError` / `GlobalExceptionHandler` — duplicate-email inserts already
      surface as 409 through the `uq_users_email` constraint
- [x] Swagger `bearer-jwt` security scheme declared (`config/OpenApiConfig.java`); adding a
      global `SecurityRequirement` turns on the Swagger **Authorize** button

### Built in Part 2

- [x] Spring Security config rewritten: stateless, JWT filter, `@EnableMethodSecurity` for the
      `@PreAuthorize` rules Parts 3–5 will add
      - `config/SecurityConfig.java` — form login and HTTP Basic both disabled; only
        `register|login|refresh|logout` plus health/Swagger are public
      - `auth/security/RestAuthErrorHandler.java` — one class serving as both
        `AuthenticationEntryPoint` (401) and `AccessDeniedHandler` (403), emitting the same
        `ApiError` JSON as `GlobalExceptionHandler`. Security rejections happen before any
        `@ControllerAdvice` runs, so they need their own writer.
- [x] `auth/service/JwtService.java` — HS256 access tokens, `sub` = userId, `email` + `role` as
      claims so authorisation costs no query
- [x] `auth/service/RefreshTokenService.java` — opaque 256-bit tokens, SHA-256 hex at rest,
      rotation on every refresh
- [x] `auth/security/JwtAuthenticationFilter.java` + `UserPrincipal` (carries `userId` for
      Parts 4–5)
- [x] `auth/service/AuthService.java` + `auth/web/AuthController.java` — `POST
      /api/auth/register|login|refresh|logout|logout-all`, `GET /api/auth/me`
- [x] Bean Validation on all five request DTOs under `auth/dto/`
- [x] Swagger **Authorize** button — `OpenApiConfig` now adds a global `SecurityRequirement`;
      the public auth endpoints opt out with `@SecurityRequirements`
- [x] **Tests: 22 passing.** `src/test` created from nothing:
      `support/AbstractIntegrationTest` (one static `mysql:8.4` container shared by the whole
      suite via `@ServiceConnection`) + `auth/AuthIntegrationTest`
- [x] **Verified:** `./mvnw clean test` → `Tests run: 22, Failures: 0, Errors: 0`. Covers
      register happy path / duplicate / validation / no self-assigned ADMIN, login for both
      seeded accounts, identical 401 for wrong-password vs unknown-email, `/me` at 401 (not
      403) and 200, refresh rotation with replay rejected, expired + revoked + unknown refresh
      tokens, logout and logout-all, and the Part 1 public routes still open.

### Part 2 deviations / notes

- **Refresh tokens are opaque random strings, not JWTs.** `refresh_tokens.token_hash` is looked
  up directly, which rules out a salted hash; SHA-256 over 256 bits of CSPRNG output has no
  low-entropy secret to brute-force, unlike a password.
- **Rotation on refresh**: the presented token is revoked and a new one issued, so a stolen
  token is single-use and a replay is a detectable 401.
- **Logout cannot kill an already-issued access token** — it stays valid until its TTL (15 min).
  Inherent to stateless JWT; a blacklist would reintroduce the server-side session store that
  TECH.md §3a explicitly rejects.
- `POST /api/auth/logout-all` was added beyond the TECH.md endpoint list, so that the
  `deleteAllByUserId` the §3a repo spec mandates is actually reachable rather than dead code.
- `POST /api/auth/logout` is **public**: it authenticates with the refresh token in the body, so
  a client whose access token already expired can still end its session.
- `PasswordEncoder` stays a plain `BCryptPasswordEncoder`. A `DelegatingPasswordEncoder` would
  fail every seeded login — those hashes have no `{bcrypt}` prefix.
- `UserDetailsServiceAutoConfiguration` is excluded in `application.yml`. Nothing in the chain
  uses a `UserDetailsService`, and leaving it on made Boot create an in-memory user and log a
  generated password at every startup.
- **CORS is deliberately not configured yet.** TECH.md §4 puts the frontend behind an Nginx
  reverse proxy, so it is only a dev-server concern — deferred to Part 6.
- **Build fix (`pom.xml`):** Docker Engine 29 dropped API versions below 1.40, but the
  docker-java bundled with Testcontainers still requests `/v1.32/`. The daemon answers 400 with
  an empty stub body and Testcontainers reports it as `Could not find a valid Docker
  environment`, which points nowhere near the cause. Surefire now passes `api.version=1.44`
  (property `docker.api.version`, overridable for older daemons).

## Part 3 — Catalog domain (venues, layouts, movies, showings) ✅ DONE

- [x] Admin CRUD: venues, rows/seats layout generation, movies, showings
      - `/api/admin/venues`, `/api/admin/movies`, `/api/admin/showings` — each controller is
        `@PreAuthorize("hasRole('ADMIN')")` at class level, with `/api/admin/**` also locked in
        `SecurityConfig` so a new endpoint is safe even if the annotation is forgotten
- [x] Public reads: `GET /api/movies` (+ `?q=` title search), `GET /api/showings?date=&venueId=`
      (+ `movieId`), `GET /api/venues`, `GET /api/venues/{id}/layout`
      - permitted **GET-only**, so the seat-hold POSTs Part 4 adds under `/api/showings/**`
        still require a token
- [x] Layout generator: `venue/service/SeatLayoutGenerator` builds rows + seats from a row-spec,
      honouring `is_aisle_gap`
      - no repositories and no transaction, so it is unit-tested without a container
      - labels default to A, B, C … and carry on past Z as AA, AB (fits `row_label VARCHAR(4)`)
      - rejects an aisle gap at or past the end of its row, and duplicate row labels, before the
        unique constraints ever see them
- [x] `ShowingSpecifications` for the admin date-range/venue/movie filters (TECH.md §3a tier 3)
      - each factory returns `null` when its filter is absent and `filter(…)` drops those, so an
        unfiltered call emits no WHERE clause rather than `1=1`
      - `@EntityGraph` override of `findAll(Specification, Sort)` keeps `movie` + `venue` eager;
        a `join fetch` inside a Specification would break the count query
- [x] Guard: reject deleting a showing that already has sold bookings (`SHOWING_HAS_BOOKINGS`),
      plus the same treatment for venues with showings (`VENUE_HAS_SHOWINGS`), movies with
      showings (`MOVIE_HAS_SHOWINGS`), and re-laying-out a venue that has bookings
      (`LAYOUT_LOCKED`)
- [x] **Tests: 64 passing** (22 from Part 2 + 42 new). `SeatLayoutGeneratorTest` (unit),
      `VenueAdminIntegrationTest`, `MovieCatalogIntegrationTest`,
      `ShowingCatalogIntegrationTest`, `CatalogAccessIntegrationTest`
- [x] **Verified:** `./mvnw clean test` → `Tests run: 64, Failures: 0, Errors: 0`. Covers the
      §6 reference layout generated end-to-end (5 rows, 29 seats, C4 labelled correctly), aisle
      gaps, layout replacement reusing the same row labels, every delete guard, all four
      Specification filters, start-time ordering, and the 401-vs-403 split for anonymous vs
      customer on admin writes.

### Part 3 deviations / notes

- **Catalogue reads are public.** TECH.md's customer flow is browse-then-book, and the handoff
  renders movie and showings pages with no auth gate. Only writes need an account.
- `GET /api/showings` with no `date` returns everything still upcoming; with one it returns that
  single UTC day. `GET /api/admin/showings` takes an inclusive `from`/`to` range instead.
- **Days are UTC boundaries**, matching `hibernate.jdbc.time_zone=UTC`. A cinema in another zone
  would want the venue's local day — added to the open items below.
- `movieId` was added to the public showings filter beyond the `?date=&venueId=` in the plan; the
  handoff's movie-detail flow needs it and the Specification was already there.
- No `@Future` on `ShowingRequest.startTime` — it would block editing the price of a showing that
  has already happened. The service enforces it on **create only** (`SHOWING_IN_THE_PAST`).
- Admin showing lists return a plain `List`, not a `Page`. 21 seeded showings do not need
  pagination; it can be added in Part 9 when the admin table exists and the shape is known.
- Layout replacement flushes **twice** on purpose: the new rows reuse the same
  `(venue_id, row_index)` and `(venue_id, row_label)` values as the old ones, so if Hibernate
  ordered the inserts before the deletes the unique constraints would fire. Covered by a test.
- `VenueRowRepository.layoutCounts()` is a tier-4 projection so the venue list can show row and
  seat totals in one query — an `@EntityGraph` over `rows` **and** `rows.seats` would be two bags
  at once, which Hibernate rejects.
- **Test-harness fix (affects Part 2 too):** `AbstractIntegrationTest` no longer uses
  `@Testcontainers`/`@Container`. That extension stops a static container in the `afterAll` of
  every class declaring it, so with a shared base class the first test class to finish killed the
  database for all the others — every later test failed with a 500 after a 10s Hikari timeout.
  Replaced with the singleton-container pattern (`static { MYSQL.start(); }` +
  `@DynamicPropertySource`), which also cut the suite runtime.

## Part 4 — Seat map + hold concurrency ⚑ flagship ✅ DONE

The feature TECH.md §5 says to treat as most important. Gets its own part and its own tests.

Everything lives in a new `com.cinema.seat` package — the feature spans venue seats, showings
and bookings without belonging to any of them, and Part 5's checkout consumes it from outside.

- [x] `GET /api/showings/{id}/seat-map` — one JPQL query for the whole map, no N+1
      (TECH.md §3a tier 2)
      - `seat/repository/SeatMapRepository` — a constructor expression into `SeatMapEntry`, so
        no entities are hydrated. A `left join Booking b on …` entity join carries the sold
        flag; `Seat` has no association to `Booking` and giving it one would hang every ticket
        ever sold off the seat
      - two round trips per request in total: this, plus the existing
        `findWithMovieAndVenueById` for the header the page needs anyway
      - readable anonymously (catalogue GETs are public); with a token, seats the caller holds
        come back flagged `heldByYou`
- [x] Redis hold: `SET hold:{showingId}:{seatId} {userId} NX EX 300`
      - `seat/service/SeatHoldStore` — the only class that speaks Redis. `NX` is a single
        command and Redis runs commands one at a time, so there is no read-then-write window
        for the loser to slip into
      - release is a **Lua compare-and-delete**, not `DEL`: between reading the holder and
        deleting, the hold can lapse and another user can take the seat — a plain delete would
        then throw away a stranger's hold
      - reading the holds on a showing uses `SCAN`, never `KEYS`. `KEYS` blocks the whole server
        for its pass, including the `SET NX` the race depends on
- [x] `POST /api/showings/{id}/seats/{seatId}/hold` and `DELETE` to release
      - `409 SEAT_HELD` / `409 SEAT_SOLD` / `400 SEAT_NOT_IN_SHOWING` / `409 SHOWING_STARTED`
      - re-holding a seat you already hold succeeds and returns the time **left**, without
        extending it — refreshing on every click would let one user sit on a seat forever
      - release is idempotent (204 when there was nothing to release), because the frontend
        releases on navigate-away and that routinely happens after the TTL has fired.
        `409 HOLD_NOT_YOURS` if the seat belongs to somebody else
- [x] Redis keyspace-expiry listener → seat reverts to `available` on TTL, broadcast the change
      - `seat/event/SeatHoldExpiryListener` on `__keyevent@*__:expired`; both ids are encoded in
        the key because the expiry message carries the key name and nothing else
      - checks `bookings.existsByShowingIdAndSeatId` before announcing: a sale racing the TTL
        would otherwise tell the room a sold seat is free
- [x] STOMP/SockJS: `/topic/showings/{id}` broadcasting `{ showingId, seatId, status }` on hold,
      release and expiry — sale joins in Part 5
      - `config/WebSocketConfig` (`/ws` endpoint, simple in-memory broker), `/ws/**` permitted in
        `SecurityConfig`
      - the topic carries **no user id**. It is readable by anyone watching the showing, so
        "who holds it" is not broadcastable; that stays on the authenticated REST read
- [x] MySQL `UNIQUE (showing_id, seat_id)` as the backstop if Redis is ever unavailable
      - Part 4 writes no bookings, so the constraint is exercised in Part 5. What Part 4 adds is
        the honest failure mode: `RedisConnectionFailureException` → `503
        SEAT_HOLDS_UNAVAILABLE` rather than a 500, with the seat map degrading to
        sold-vs-available straight from MySQL
- [x] **Tests: 100 passing** (64 from Parts 1–3 + 36 new). `HoldKeyTest` (unit),
      `SeatMapIntegrationTest`, `SeatHoldIntegrationTest`, `SeatHoldConcurrencyIntegrationTest`,
      `SeatHoldExpiryIntegrationTest`
- [x] **Verified:** `./mvnw clean test` → `Tests run: 100, Failures: 0, Errors: 0`. Covers 24
      threads released through a `CyclicBarrier` onto one seat with exactly one winner and 23
      `SEAT_HELD` rejections, Redis agreeing with the winner and a single broadcast going out;
      every racer taking a different seat succeeding; a hold expiring on a 1s TTL, broadcasting
      `AVAILABLE` and freeing the seat for the next user; an expiry after a sale broadcasting
      `SOLD` instead; a sold seat refusing a hold; releasing someone else's hold; the seat map
      grouped into rows with `heldByYou` visible only to its owner; and holds staying scoped to
      their own showing.

### Part 4 deviations / notes

- **A hold is a Redis key, never a `bookings` row.** `BookingStatus.HELD` stays unused, exactly
  as its javadoc from Part 1 says: an expiring hold should cost no database write.
- **`SeatService` is deliberately not `@Transactional`.** Every read is a single query returning
  DTOs or an eagerly-fetched entity, so there is nothing to lazy-load and nothing to roll back —
  and a transaction would pin a database connection for the length of a Redis round trip on the
  two hottest endpoints in the app.
- **Broadcasts are a hint, not a source of truth.** Keyspace notifications are plain pub/sub with
  no delivery guarantee, and Redis fires them when it actually reaps the key rather than exactly
  at the TTL. `GET /seat-map` therefore recomputes from Redis + MySQL on every read and never
  from accumulated events, so a client that missed a frame is made whole by a refresh.
- **Seat ids are global, not per-showing**, so every hold proves the seat is really in the
  showing's auditorium (`SeatRepository.findInVenue`, which returns the row in the same query and
  so names the seat in the error message).
- `setAllowedOriginPatterns("*")` on the SockJS endpoint is a **dev-server allowance**. In
  production the SPA is same-origin behind Nginx (TECH.md §4); it gets narrowed with the rest of
  CORS in Part 6.
- **Test harness:** `AbstractIntegrationTest` now starts a Redis container too — the expiry
  listener opens its subscription during context startup, so every context needs one. It runs
  with `--notify-keyspace-events Ex` to match docker-compose; without that flag Redis honours the
  TTL but publishes nothing, and the expiry tests would hang waiting on a frame that never comes.
- **Broadcasts are asserted by subscribing a handler to `brokerChannel`**
  (`support/SeatBroadcasts`) rather than by mocking the publisher. It exercises the real
  destination and the real JSON converter, and needs no `@MockitoSpyBean`, so the context is not
  dirtied and the suite keeps sharing one. The channel delivers on its own executor, so tests
  await frames instead of draining immediately.
- `SeatHoldExpiryIntegrationTest` overrides `app.seat-hold.ttl-seconds=1` and therefore gets its
  own application context; the MySQL and Redis containers are still shared.
- **Not built, deliberately:** no cap on how many seats one user may hold at once. It belongs
  with the checkout rules in Part 5 — added to the open items below.

## Part 5 — Booking / checkout + admin reporting ✅ DONE

- [x] `POST /api/bookings` — validate the hold still belongs to the caller, write `sold` rows in
      one transaction, delete the Redis keys, broadcast `sold`
      - `booking/service/BookingService.confirm` — the basket is **all-or-nothing**: one lapsed
        hold rejects the whole purchase and writes nothing
      - **the Redis delete and the broadcast happen after the transaction commits**
        (`TransactionSynchronization.afterCommit`), not inside it. Announcing a sale that a
        rollback then undid would leave every open seat map showing seats as permanently sold
        that nobody owns — and only a page refresh, not another broadcast, would clear it
      - `forceRelease` rather than the compare-and-delete used elsewhere: the hold has just been
        proven to belong to this buyer, and the sale supersedes it either way. `DEL` fires no
        expiry event, so the only frame the room sees for those seats is the `SOLD` one
      - failure codes are distinct because the UI reacts differently to each: `HOLD_EXPIRED`
        (send them back to the seat map), `SEAT_HELD` (someone else took it), `SEAT_SOLD`
        (double-confirm), `SEAT_NOT_IN_SHOWING`, `SHOWING_STARTED`
      - `bookings.saveAll` flushes the basket in one go, so `uq_bookings_showing_seat` is the
        backstop if Redis was down and two checkouts slipped past the hold check
      - price is snapshotted per row from `showings.price`; the client never sends a total
- [x] Cap how many seats one user may hold at once — `app.seat-hold.max-seats-per-user: 10`
      - counted **per showing**, from Redis rather than a counter of our own, so a lapsed hold
        stops counting the moment it expires
      - re-holding a seat you already hold is exempt, or it would break Part 4's idempotent
        re-click
- [x] `GET /api/bookings/me` (upcoming/past split), `GET /api/bookings/{ref}`
      - the split is computed server-side off `showings.start_time`, so both tabs agree with the
        server about where "now" is
      - **a reference belonging to someone else answers 404, not 403.** A 403 confirms the
        reference exists, which turns the endpoint into an oracle for guessing booking codes
      - rows are folded back into one purchase per reference; seats are sorted by label so a
        ticket reads A1, A2, A3 regardless of click order
- [x] Admin: `GET /api/admin/bookings` with `BookingSpecifications` (date range, venue, movie,
      status)
      - dates filter on the **showing's** start time, not the purchase time — an admin asking for
        a weekend means the screenings. The purchase timeline is what revenue-by-date is for
      - the specs `join("showing")` once and reuse it; a path expression through a to-one
        association emits a fresh join per use, so filtering on two showing fields would produce
        two
      - admin rows carry a `customer` block that the customer's own view does not
- [x] Analytics projections: `BookingsPerShowingView`, `RevenueByDateView` (TECH.md §3a tier 4)
      - `GET /api/admin/analytics` — four counters, top showings by revenue, revenue by date
      - purchases are counted as `count(distinct bookingReference)`; tickets as rows. Three seats
        on one reference is one sale and three tickets
      - revenue-by-date is keyed on **purchase** day via `cast(b.createdAt as Date)`, which is how
        JPQL truncates a timestamp and is what lets the projection expose a `LocalDate`
- [x] **Tests: 129 passing** (100 from Parts 1–4 + 29 new). `CheckoutIntegrationTest`,
      `BookingHistoryIntegrationTest`, `AdminBookingIntegrationTest`,
      `SeatHoldLimitIntegrationTest`
- [x] **Verified:** `./mvnw clean test` → `Tests run: 129, Failures: 0, Errors: 0`. Covers
      double-confirm rejected with exactly one row surviving, an expired hold rejected, a
      partial basket writing nothing while leaving the good hold intact, buying a seat someone
      else holds, a duplicated seat collapsing to one ticket, the `SOLD` broadcast and hold
      cleanup, the 404-not-403 rule for a stranger's reference, upcoming/past moving when the
      showing is backdated, all four admin filters, analytics totals and both series, and the
      hold cap per user and per showing.

### Part 5 deviations / notes

- **Booking references are `LUM-` + 6 characters from a 30-symbol alphabet**, not the handoff's
  `LUM-77291`. Uniqueness cannot be a database constraint here — the reference is deliberately
  shared by every row of one purchase — so it is established by generating from a ~729 million
  space and checking `existsByBookingReference`, with bounded retries. Five digits (90k) would
  collide at a few hundred bookings. I, L, O, U, 0 and 1 are excluded: the code gets read aloud
  at a counter.
- **`BookingStatus.HELD` is still never written.** Holds live in Redis (Part 4), so the status
  filter on the admin screen returns nothing for `HELD` by design — asserted in a test so the
  behaviour is deliberate rather than an accident waiting to be "fixed".
- Admin bookings return a plain `List`, matching the Part 3 precedent. Pagination is Part 9's,
  when the table exists and its shape is known — and it needs care, because paginating rows would
  split a purchase across pages.
- **Analytics counters are platform-wide and unfiltered by date**; only the two series honour
  `since`. The dashboard's headline numbers are "all time", which is what the counters mean.
- `confirm` collapses a duplicated seat id rather than rejecting the basket, so a double-submit
  buys one ticket instead of erroring.
- **Payment stays simulated** per TECH.md §Payments — confirming is the whole transaction.
- Test-rig change: `AbstractSeatIntegrationTest` is now public with a `holdStore` handle and
  `hold` / `checkoutBody` helpers, so the booking tests in another package can build on the same
  auditorium fixture instead of duplicating it.

## Part 6 — Frontend scaffold + handoff cleanup ✅ DONE

Taken before Part 5 at the user's request. Nothing here touches the API, so the reorder cost
nothing: Part 7 is the first part that needs Part 5's endpoints.

The handoff bundle already sat at `/frontend` (not `design_handoff_movie_booking/` as this plan
originally said) — `README.md`, `tailwind.config.ts` and `src/`, with no way to run any of it.

- [x] `/frontend` Vite 5 + React 18 + TS + Tailwind 3 (`darkMode: 'class'`), router, entry files
      - added `package.json`, `index.html`, `vite.config.ts`, `tsconfig.json`,
        `postcss.config.js`, `src/main.tsx`, `src/index.css`, `src/vite-env.d.ts`,
        `public/favicon.svg`
      - **one tsconfig, not the template's src/node split.** The split exists so the Vite config
        can compile with Node types under `composite`, and `composite` forbids `noEmit` — which
        this project needs, since Vite emits and `tsc` is only ever a checker here. `tsc -b`
        fails outright on that combination (`TS6310`). Build script is `tsc --noEmit && vite build`
      - dev server **proxies `/api` and `/ws`** to `localhost:8080`, mirroring the Nginx rules in
        TECH.md §4. This closes the CORS question Part 2 deferred here: the browser only ever
        talks to one origin, so the backend still needs no CORS config. `ws: true` on the `/ws`
        rule, or the SockJS upgrade 404s in Part 8
      - `index.html` applies the stored theme **before first paint**; React mounts after the
        browser has painted, so without it every load flashed white for dark-mode users
- [x] Ported the handoff in and **fixed every known defect**:
      - `CheckoutPage` / `ConfirmationPage` duplicate `style` — JSX keeps only the last
        attribute, so the poster gradient lost to an inline width/height and both posters
        rendered blank. Sizes are classes now and the inline overrides are gone
      - the eight dead Tailwind classes are **real values in `theme.spacing`** (`4.5`, `7.5`,
        `13`, `14.5`, `19`, `21`) rather than being rewritten. The handoff author was writing the
        sizes they wanted; making them exist fixes the classes and the duplicate `style` in one
        move. Verified in the built CSS: `.h-7\.5{height:1.875rem}`, `.w-13{width:3.25rem}`, …
      - `ShowingsPage` — both filters work, the venue list is **derived from the showtimes**
        rather than a second hardcoded list, and a date with no showings gets an empty state
      - `SeatSelectionPage` — available is `bg-seat-available` + a border, per the README. The
        bundle's `bg-zinc-200 dark:bg-zinc-100` with no border was the same grey as the disabled
        button, so free seats read as unavailable
      - `NavBar` is destinations only (Showings, My Bookings, Log In). `/seats`, `/checkout` and
        `/confirmation` are flow steps behind `RequireBookingState`, so Checkout can no longer be
        opened cold and asked to confirm a purchase of nothing. `/` lands on Showings, not Login —
        the catalogue is public (Part 3)
- [x] Tailwind theme extended with **semantic tokens instead of raw palette names**
      - `page`, `surface`, `raised`, `sunken`, `elevated`, `line`, `line-strong`, `ink`, `muted`,
        `disabled`, `accent`, `accent-ink`, `accent-text`, `ok`, `ok-ink`, `seat-*`
      - backed by CSS variables in `index.css` as `R G B` channel triplets composed through
        `rgb(var(--token) / <alpha-value>)`, so opacity modifiers like `border-accent/70` survive
      - `dark:` is now **gone from the markup entirely** — the only occurrence left in `src/` is
        inside a comment quoting the old seat colours
- [x] **Verified:** `npm run build` clean (`tsc --noEmit` + Vite, 45 modules, 14.8 kB CSS /
      184.7 kB JS), and the app driven end to end in a real browser (headless Edge via
      playwright-core) with **zero console errors**:
      - `/` → `/showings`; date and venue filters narrow the list correctly (Today = 4 movies,
        Tomorrow = 5, Tomorrow + Riverside IMAX = 2, Thu + Riverside = empty state)
      - cold jumps to `/checkout`, `/confirmation` and `/seats` all bounce to `/showings`
      - showtime → seat map → pick A1 + B1 → "2 seats selected · $28" → checkout total $28
      - the checkout poster is 64×84 **with** its gradient — the defect that used to blank it
      - Confirm Purchase → confirmation with a generated ref → the booking appears at the top of
        My Bookings → Upcoming
      - light theme: `<html>` loses `dark`, and the seat map reads
        available `rgb(255,255,255)` + 1px border, reserved `rgb(239,68,68)`,
        selected `rgb(16,185,129)`

### Part 6 deviations / notes

- **`SEAT_PRICE = 14` and `mockData.ts` are still in place.** Part 6's brief is "renders on mock
  data"; deleting the mock source is Part 7's job, and `posterGradient` stays permanently.
- `VENUES` is derived from `MOVIES` rather than listed separately, so the filter cannot drift
  from the data it filters.
- **The model mismatch is untouched.** The handoff still speaks in display strings (`'Today'`,
  `'7:00 PM'`) where the API returns a `showingId` and an ISO `startTime`; reconciling that is
  explicitly Part 7's item.
- **No ESLint.** Part 10's `frontend-ci.yml` wants a lint step; `npm run build` type-checks via
  `tsc --noEmit` in the meantime. Added to Part 10 below.
- `public/favicon.svg` was added because the browser's `/favicon.ico` request was the only thing
  producing console noise.
- The browser verification used `playwright-core` installed **outside the repo** (scratchpad),
  driving the already-installed Edge. Nothing was added to `package.json` for it and no browser
  binary was downloaded.

## Part 7 — Wire frontend to the real API ✅ DONE

Every screen reads the server now. `mockData.ts` is gone.

- [x] **Axios client with the JWT interceptor + silent refresh on 401** — `api/client.ts`,
      `api/tokens.ts`
      - `baseURL: '/api'` and deliberately no `VITE_API_URL` escape hatch: the dev proxy and
        Nginx both put the API on the page's own origin (TECH.md §4), so introducing a base URL
        would mean introducing CORS
      - **single-flight refresh.** `/auth/refresh` *rotates* — it revokes the token it was
        handed — so two parallel 401s each refreshing would leave the second presenting a token
        the server had already killed. Every caller awaits one shared promise instead
      - the refresh itself goes through bare `axios`, not the instance, or a failing refresh
        would recurse into refreshing itself
      - `_retried` marks a replayed request, so a persistent 401 gives up instead of looping
      - fires on 401 only. The backend's 401-vs-403 split (`RestAuthErrorHandler`) is what makes
        that safe: a customer hitting an admin route gets a 403 and is not retried
      - auth routes are excluded both ways — no `Authorization` header on them (a stale token
        would 401 the very call meant to establish the session) and no refresh on their failures
      - `errorCode()` / `errorMessage()` — screens branch on `SEAT_HELD`, `HOLD_EXPIRED`, … and
        show the server's message, never "Request failed with status code 409"
- [x] **`AuthContext` + route guards** — real login/register/logout, `RequireAuth` with an
      optional `role` for Part 9's admin section
      - a stored token is verified against `/auth/me` on load rather than trusted; a lapsed
        access token recovers through the same silent refresh instead of ending the session
      - `isLoading` is what keeps a page refresh from signing you out: the user is briefly null
        while `/auth/me` is in flight, and the guard must not redirect during that window
      - sign-in and sign-out call `queryClient.clear()` — `heldByYou` and `/bookings/me` mean
        something different once the caller changes
      - a rejected refresh token dispatches a window event the provider listens for; the axios
        interceptor is module scope and cannot call `setState`
      - tokens in `localStorage`, every access wrapped in try/catch (Safari private mode throws
        on both read and write, and a storage failure must not take the page down)
- [x] **TanStack Query everywhere** — `api/hooks.ts`. Every `// TODO: replace with useQuery(...)`
      marker is gone
      - reads: `useMovies`, `useVenues`, `useShowings`, `useSeatMap`, `useMyBookings`
      - writes: `useHoldSeat`, `useReleaseSeat`, `useConfirmBooking`
      - hold/release patch the cached seat map in place instead of refetching it — the map is
        the biggest payload in the flow and the server has already confirmed the change.
        `availableCount` is recounted rather than nudged by ±1, so it cannot drift out of step
        with the grid it counts
      - `retry` is off for 4xx: a 409 is an answer, not a hiccup, and retrying it only delays
        the message the customer needs to read
- [x] **`mockData.ts` deleted.** `posterGradient` moved to `lib/poster.ts` — it was never mock
      data, it is how a poster is drawn from `movies.poster_hue`. `SEAT_PRICE = 14` is gone;
      price comes off the showing. `src/types.ts` is down to `Theme`, with the API's shapes in
      `api/types.ts` mirroring the backend records one for one
- [x] **Model mismatch reconciled** — `lib/datetime.ts`, the one place that turns instants into
      the handoff's display strings
      - the day chips are **derived from the clock**. The handoff hardcoded
        `['Today','Tomorrow','Wed','Thu','Fri']`, which was only ever right on a Monday
      - **everything is formatted in UTC**, because the backend buckets days at UTC midnight
        (`ShowingService.findPublic`). Rendering in the browser's zone would let the chip and
        the time printed under it disagree about which day a 23:30 screening belongs to. The
        venue-local fix is still on the open-items list below; `DISPLAY_ZONE` is the single line
        it would change
      - showings that have already started are dropped from the listing: a date filter returns
        the whole calendar day, and both `SeatService` and `BookingService` refuse a started
        showing with `SHOWING_STARTED`, so offering one is a button that can only fail

### Built beyond the brief (small, and load-bearing)

- [x] **`posterHue` added to `BookingResponse`** (the only backend change). The confirmation and
      My Bookings screens draw a poster, and this was the one movie-carrying DTO that did not
      serve the hue — the alternative was inventing a second, disagreeing way to colour the same
      movie.
- [x] **Held seats are visually distinct from sold ones.** The API has always returned `HELD` vs
      `SOLD`; the mock had only "reserved". A held seat is somebody mid-checkout whose TTL may
      lapse, so it is amber and worth waiting for — a second red would read as sold out. New
      `--seat-held` token; `--danger` / `--danger-surface` added for the error panels.
- [x] **The seat map stays public; the sign-in prompt moved to the seat click.** `GET` seat-map
      is permitAll and holding is not, so browsing anonymously works and the prompt arrives at
      the moment an account is actually needed. The redirect carries the location, so signing in
      returns to the seat map with the showing intact.
- [x] **Holds survive a reload.** A hold is a Redis key, not component state, so the seat map's
      `heldByYou` flags are adopted into the booking context once per showing. Without it a
      reload mid-selection showed selected seats the checkout guard could not see.

### Tests

- [x] **Frontend: 34 passing** (7 from Part 6 + 27 new). `npm test`
      - `api/client.test.ts` (14) — the interceptor, stubbed at the adapter seam so the real
        interceptors, the real single-flight promise and the real token store all run. Covers
        the header rules, refresh-and-replay, **three parallel 401s spending the refresh token
        once**, session teardown on a rejected refresh, no-loop after one replay, the 403 and
        missing-refresh-token cases, and `errorCode`/`errorMessage` extraction
      - `lib/datetime.test.ts` (13) — the agreement with the backend: chip values are the
        `yyyy-MM-dd` the API buckets on, a 23:30 UTC showing stays on the day its query filed it,
        and month/leap-day boundaries do not produce a 32nd
      - `RequireBookingState.test.tsx` (7) — updated for the new context shape; every redirect
        rule held
- [x] **Backend: 129 passing**, unchanged by the `posterHue` addition.
      `./mvnw clean test` → `Tests run: 129, Failures: 0, Errors: 0`

### Verified in a real browser

Headless Edge via `playwright-core` (installed outside the repo, nothing added to
`package.json`, no browser binary downloaded), against the real stack: MySQL + Redis in Docker,
Spring Boot on `local`, Vite dev server. **Zero application console errors throughout.**

Happy path:

- `/` → `/showings`; Today lists 2 movies, Tomorrow 4, Tomorrow + Uptown Cineplex 2,
  Today + Downtown 8 1, and a day with no showings gets the empty state
- anonymous cold jumps: `/seats` → `/showings`; `/checkout`, `/confirmation`, `/bookings` →
  `/login` (auth is the outer guard)
- the seat map renders anonymously; clicking a seat → `/login` → signing in returns **to the
  seat map**, showing still selected
- hold one seat ("1 seat selected · $18.50") → release ("Select your seats") → hold two
  ("2 seats selected · $37.00"), priced off `showings.price` rather than a constant
- a seat held by another customer renders "(on hold)", amber and disabled
- reload mid-selection → both seats still selected, adopted from `heldByYou`
- checkout `Seats A6, A7 | Price per ticket $18.50 | Total $37.00`, poster gradient intact (the
  Part 6 duplicate-`style` defect stays fixed) → Confirm → **server-minted** `LUM-QN7D6P`
- My Bookings: Upcoming/Past come from the server's split; each row opens its own reference
- the bought seats come back sold; the session survives a full reload; Log Out clears both
  tokens and returns the nav to its signed-out state

Edge paths a happy-path run cannot reach:

- **silent refresh** — with the access token replaced by garbage and the refresh token kept,
  `/bookings` renders normally, `POST /api/auth/refresh` is called **exactly once** (two
  concurrent 401s, from StrictMode's double-invoked effect — the single-flight guard doing its
  job), the refresh token rotates, and the customer never sees a login form
- **rejected refresh token** → both tokens cleared, redirect to `/login`
- **lost seat race** — another customer takes the seat between render and click →
  *"Seat B1 is being held by someone else. Pick another seat."*, the optimistic selection
  reverts, and B1 repaints as "(on hold)" with no manual refresh

### Part 7 deviations / notes

- **`RequireAuth` wraps `RequireBookingState`, not the other way round.** An anonymous cold jump
  to `/checkout` therefore lands on `/login` rather than `/showings`. Signing in returns them to
  `/checkout`, which then bounces to `/showings` because there is still no booking state — two
  hops, but each guard answers only the question it owns.
- **Seat holds are not released on navigate-away.** Leaving the seat map abandons them to their
  TTL. Eager release is explicitly Part 8's item, together with the countdown; `expiresAt` is
  already carried on `HeldSeat` and goes unused for now.
- **The seat map has no live updates yet** — another customer's hold appears on the next fetch
  (mount, window focus, or a failed hold of your own), not instantly. That is Part 8's subject.
- **Admin-only routing is wired but has nothing behind it.** `RequireAuth role="ADMIN"` is
  written and unused until Part 9 adds the screens.
- **The seeded showings had all expired**, exactly as the open item below predicted. Seven fresh
  showings were created **through the Part 3 admin API** for the verification rather than
  recreating the volume, so nothing existing was destroyed.
- `qrcode.react` is still not installed; the confirmation QR stays a CSS placeholder.
- Part 10's ESLint item matters more now: `api/`, `lib/` and the contexts roughly doubled the
  source, and `tsc --noEmit` is still the only static check.

## Part 8 — Live seat selection (frontend concurrency)

- [ ] SockJS + STOMP client hook subscribing to `/topic/showings/{id}` — endpoint is `/ws`, the
      frame is `{ showingId, seatId, status }`, and the countdown is driven by
      `holdTtlSeconds` / `expiresAt` off the Part 4 responses rather than a hardcoded 300
- [ ] Seat map reflects other users' holds live; own selection stays optimistic
- [ ] Hold countdown timer with expiry warning, auto-release on unmount/navigate-away
- [ ] Rejected-hold path: seat taken mid-click → revert + inline message

## Extra — real catalogue from TMDB (out of band, requested mid-Part-8)

The seeded five movies were placeholders with a gradient where a poster should be. The listing
now comes from themoviedb.org's weekly trending chart, with real artwork.

**Why the import lives on the backend and not in the browser:** showings carry a foreign key to
`movies`, so a movie the frontend fetched for itself could never be scheduled, and calling TMDB
from the page would mean either shipping the API key to the browser or introducing the CORS the
whole `/api` same-origin design (TECH.md §4) exists to avoid.

- [x] **`V20260827_140000__movie_artwork.sql`** — `movies.poster_url` and a nullable-unique
      `movies.tmdb_id`
      - `poster_hue` **stays**. The initial schema called it "the placeholder until real artwork
        exists"; it turns out to still be load-bearing as what fills the frame while a 500px JPEG
        crosses the network, and as the whole poster for a movie added by hand through the admin
        API. It is painted under the image rather than replaced by it
      - `tmdb_id` is nullable so the seeded and hand-entered movies are not forced to invent an
        identity, and unique so a re-import updates rather than duplicates (MySQL allows any
        number of NULLs in a UNIQUE index)
- [x] **`catalog/tmdb/TmdbClient`** — the only class that talks to themoviedb.org
      - **works out its own auth style.** TMDB's settings page offers a v3 API key *and* a v4
        read access token and does not make clear they authenticate differently; the shape of the
        value decides (a v4 token is a JWT → `Authorization: Bearer`, a v3 key → `api_key=`)
      - `/trending/movie/week` rather than `/movie/popular`: popular is dominated by the same
        evergreen blockbusters, trending actually turns over
      - `append_to_response=release_dates` folds the certification sub-resource into the detail
        call, halving the round trips an import makes
      - 5s connect / 15s read timeouts, because a third party on the public internet must not pin
        a request thread, and every failure becomes a coded `ApiException` — `TMDB_UNAUTHORIZED`,
        `TMDB_RATE_LIMITED`, `TMDB_UNAVAILABLE`, `TMDB_NOT_CONFIGURED` — rather than a 500
- [x] **`catalog/service/MovieImportService`** — the mapping, and the schedule that makes it
      visible
      - **a movie with no showing is invisible.** The customer flow is driven entirely by
        `GET /showings?date=`, so an import that only wrote `movies` rows would look like it had
        done nothing. Importing and scheduling are one operation
      - **walks the chart rather than slicing it.** An entry can be unusable — announced but
        unreleased, so no runtime, or no artwork — and stopping at the first ten would quietly
        deliver eight
      - certification falls back to `NR` (the column is NOT NULL and TMDB has none for a film not
        yet rated in the region); genre falls back to `Feature`
      - `posterHue` is derived as `tmdbId * 137 mod 360` — stable across re-imports, and 137 being
        coprime with 360 keeps neighbouring ids from landing in a run of near-identical blues
      - **new showings inherit the venue's own established price** (`findLatestPriceForVenue`)
        rather than a constant invented in the importer, so the IMAX stays dearer than the
        multiplex
      - slots already in the past are skipped: `SeatService` and `BookingService` both refuse a
        started showing, so scheduling one creates a listing entry that can only fail
- [x] **Nothing is destroyed.** `fk_showings_movie` and `fk_bookings_showing` have no cascade, and
      a sold ticket is not the importer's to delete. Only *upcoming* showings with **no bookings**
      are cleared, via a `not exists` subquery — seeded movies keep their rows, their past
      showings and every booking against them, and My Bookings history is untouched.
- [x] **`POST /api/admin/catalog/import-featured?limit&days&replaceUpcoming`** — admin-only, and
      **explicitly triggered**. Not on a schedule and not at startup: it is the one operation that
      reaches out to a third party, and a boot that silently depends on a public API being up (and
      a key being set) is a boot that fails in CI for reasons nothing in the codebase explains.
      No key configured → `503 TMDB_NOT_CONFIGURED`, which is why the whole suite still runs
      offline.
- [x] **`posterUrl` added to the three DTOs that carry a movie** — `MovieResponse`,
      `ShowingResponse.MovieSummary` and `BookingResponse`. `MovieRequest` gained it too, so Part 9's
      admin form can set artwork by hand.
- [x] **Frontend `components/Poster.tsx`** — artwork over the gradient
      - the gradient is *always* the element's background and the image sits on top, so there is
        no loading flash to manage and no layout shift; a failed image simply reveals what was
        already behind it
      - `onError` falls back, and the failure state **resets per URL** — lists recycle the same
        component for different movies, and without that one dead image would leave every later
        card in that slot showing the placeholder
      - grid cards moved to a real `aspect-[2/3]` poster frame. The handoff's 128px banner was the
        right shape for a gradient and the wrong one for a poster: it cropped the faces off
- [x] **Tests: backend 145** (was 129, +16), **frontend 67** (was 61, +6)
      - `catalog/TmdbMovieTest` (6) — the two bits of TMDB's payload that need interpreting:
        certification is nested three deep by territory *and* release type with most entries
        carrying `""` rather than being absent, so "first non-blank in region" is the rule
      - `catalog/CatalogImportIntegrationTest` (10) — TMDB mocked at the client seam, so the suite
        stays offline. Covers the mapping, idempotent re-run, **booked showings surviving a
        replace**, `replaceUpcoming=false`, the chart-walk past unusable entries, one movie 404ing
        without losing the run, an auth failure aborting instead of skipping every movie in turn,
        admin-only access, and argument bounds. `@Transactional` (unlike the rest of the suite)
        because these tests genuinely conflict: the import is idempotent by `tmdb_id` and skips a
        venue slot already busy, so without rollback "created 3" means something different in the
        second method than the first
      - `components/Poster.test.tsx` (6) — artwork, no artwork, **an absent field**, a failed
        load, the gradient being identical either way, and the retry after a previous movie's
        image failed

### Extra — deviations / notes

- **Apple's movies RSS feed was the no-key candidate and it is retired** (404; only apps, music
  and books remain), and the iTunes Search API has no featured/top-10 concept at all. TMDB needs
  a free key, which is why the import is configuration-driven rather than hardcoded.
- **`posterUrl` arrives absent, not null.** `default-property-inclusion: non_null` is app-wide
  (it is what hides `customer` on a customer's own booking), so a movie with no artwork has no
  `posterUrl` key at all. The first cut of `Poster` guarded with `posterUrl !== null`, which
  `undefined` sails straight past: every card rendered an `<img>` with no `src` — a permanently
  broken frame that never fires `onError`, so the gradient fallback never got its turn. The guard
  is truthiness now and the TS types say `posterUrl?:` to match the wire. Caught in the browser;
  the unit tests had only ever been handed an explicit `null`.
- **`TMDB_NOT_CONFIGURED` was found by running the app, not by the tests** — the integration test
  mocks `TmdbClient`, so `requireConfigured()` never fires in it. A stale backend process was
  also masking the new controller behind a `NoResourceFoundException`; both were caught by curling
  the real endpoint.
- Poster images load from `image.tmdb.org`, the first third-party origin the page talks to. Fine
  today (no CSP is set); worth an explicit `img-src` allowance whenever one is added in Part 10.
- The importer schedules 4 slots × every venue × N days, rotating the movie so the same film is
  not always the 19:30. With 3 venues and 5 days that is ~60 showings for 10 movies.

## Part 9 — Admin frontend

Designed fresh against the Part 6 tokens (`surface`, `line`, `accent`, `muted`, …) rather than
raw palette names, so the admin chrome inherits light/dark for free.

- [ ] Admin shell + nav, separate from the customer chrome
- [ ] Venues list/editor + visual seat-layout builder (rows, seats-per-row, aisle gaps)
- [ ] Movies CRUD, showings CRUD (movie × venue × time × price)
- [ ] All-bookings table with the Part 5 filters
- [ ] Analytics dashboard: counters + revenue-by-date

## Part 10 — Testing, containerisation, CI, docs

- [ ] Testcontainers MySQL for the whole integration suite (TECH.md §3 — real MySQL, not H2)
- [ ] Backend `Dockerfile` (multi-stage), frontend `Dockerfile` (build → Nginx)
- [ ] `nginx.conf`: SPA fallback to `index.html`, `/api/*` reverse proxy (TECH.md §4)
- [ ] `docker-compose.yml` (dev) and `docker-compose.prod.yml` — frontend, backend, mysql, redis
- [ ] ESLint for `/frontend` + an `npm run lint` script (Part 6 shipped without it; the build's
      `tsc --noEmit` is the only static check today)
- [ ] `.github/workflows/backend-ci.yml` + `frontend-ci.yml`
- [ ] `README.md`: run instructions, seeded credentials, architecture notes

---

## Open items (not blocking — flagged as they come up)

- Seat pricing lives on `showings.price` in TECH.md §6, but the handoff hardcodes a flat
  `SEAT_PRICE = 14`. Backend is authoritative; the flat constant goes away in Part 7.
- TECH.md §6 has no `seat_type` pricing multiplier even though `seats.seat_type` exists —
  treated as a display-only label for v1.
- ~~Booking reference is client-generated in the handoff~~ — server-side as of Part 5,
  `LUM-` + 6 chars from an unambiguous 30-symbol alphabet. The frontend mock still mints its
  own; that goes away in Part 7.
- Showing day boundaries are UTC (Part 3). Correct while the whole stack runs UTC, but a real
  chain needs the *venue's* local day — otherwise a 00:30 screening lands on the previous day's
  listing. Would mean a timezone column on `venues`.
- Seeded showings are positioned relative to `CURDATE()` at first migration run. Flyway applies
  a migration once, so the seeded week does not roll forward — after a few days the "Today"
  chip goes empty until showings are added through the Part 3 admin screens (or the volume is
  recreated with `docker compose down -v`).
