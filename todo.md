# Cinema Seat Booking — Execution Plan

Source of truth: `TECH.md`. UI source: `design_handoff_movie_booking/` (customer flow only —
admin screens do not exist yet and get designed from the same Tailwind tokens).

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

## Part 4 — Seat map + hold concurrency ⚑ flagship

The feature TECH.md §5 says to treat as most important. Gets its own part and its own tests.

- [ ] `GET /api/showings/{id}/seat-map` — single fetch-join JPQL query returning seats +
      current status, no N+1 (TECH.md §3a tier 2)
- [ ] Redis hold: `SET hold:{showingId}:{seatId} {userId} NX EX 300` — `NX` is what makes the
      race safe; the loser of a tie gets a rejection, not a second hold
- [ ] `POST /api/showings/{id}/seats/{seatId}/hold` and `DELETE` to release
- [ ] Redis keyspace-expiry listener → seat reverts to `available` on TTL, broadcast the change
- [ ] STOMP/SockJS: `/topic/showings/{id}` broadcasting `{ seatId, status }` on hold, release,
      expiry, and sale
- [ ] MySQL `UNIQUE (showing_id, seat_id)` as the backstop if Redis is ever unavailable
- [ ] **Tests:** concurrent hold test — N threads race for one seat, assert exactly one wins;
      TTL expiry reverts status; a user cannot hold a sold seat

## Part 5 — Booking / checkout + admin reporting

- [ ] `POST /api/bookings` — validate the hold still belongs to the caller, write `sold` rows in
      one transaction, delete the Redis keys, broadcast `sold`
- [ ] `GET /api/bookings/me` (upcoming/past split), `GET /api/bookings/{ref}`
- [ ] Admin: `GET /api/admin/bookings` with `BookingSpecifications` (date range, venue, status)
- [ ] Analytics projections: `BookingsPerShowingView`, revenue-by-date (TECH.md §3a tier 4)
- [ ] Tests: double-confirm is rejected, expired hold is rejected, totals are correct

## Part 6 — Frontend scaffold + handoff cleanup

- [ ] `/frontend` Vite + React 18 + TS + Tailwind (`darkMode: 'class'`), router, entry files
      (the handoff bundle is loose source — it has no `package.json`, `index.html`, or `main.tsx`)
- [ ] Port `design_handoff_movie_booking/src` in and **fix the known defects**:
      - `CheckoutPage.tsx:23` and `ConfirmationPage.tsx:24` — duplicate `style` attribute on one
        element; React keeps only the last, so the poster gradient never renders
      - invalid Tailwind classes throughout: `h-7.5`, `w-4.5`, `w-13`, `h-21`, `h-19`, `w-14.5`,
        `mb-4.5`, `pt-4.5` — none exist in the default v3 scale and silently do nothing
      - `ShowingsPage` — `dateFilter` state and the venue `<select>` are wired to nothing; the
        movie list ignores both
      - `SeatSelectionPage` — available-seat colour contradicts the README (`bg-zinc-200` /
        `dark:bg-zinc-100` vs "white/zinc-100 with border")
      - `NavBar` exposes every route as a flat tab, including `/checkout` and `/confirmation`
        with no state behind them — becomes real navigation plus a guarded flow
- [ ] Extend the tailwind theme with the actual tokens instead of raw palette names
- [ ] **Verify:** `npm run build` clean, `npm run dev` renders all six screens on mock data

## Part 7 — Wire frontend to the real API

- [ ] Axios/fetch client with the JWT interceptor + silent refresh on 401
- [ ] `AuthContext` (real login/register/logout), route guards, admin-only routes
- [ ] TanStack Query for movies, showings, seat map, bookings — replacing every `// TODO:
      replace with useQuery(...)` marker in the handoff
- [ ] Delete `mockData.ts` as a data source; keep `posterGradient` as a UI helper
- [ ] Reconcile the model mismatch: the handoff uses display strings (`'Today'`, `'7:00 PM'`)
      where the API returns a `showingId` + ISO `start_time`

## Part 8 — Live seat selection (frontend concurrency)

- [ ] SockJS + STOMP client hook subscribing to `/topic/showings/{id}`
- [ ] Seat map reflects other users' holds live; own selection stays optimistic
- [ ] Hold countdown timer with expiry warning, auto-release on unmount/navigate-away
- [ ] Rejected-hold path: seat taken mid-click → revert + inline message

## Part 9 — Admin frontend

Designed fresh against the handoff's tokens (teal accent, zinc surfaces, light/dark).

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
- [ ] `.github/workflows/backend-ci.yml` + `frontend-ci.yml`
- [ ] `README.md`: run instructions, seeded credentials, architecture notes

---

## Open items (not blocking — flagged as they come up)

- Seat pricing lives on `showings.price` in TECH.md §6, but the handoff hardcodes a flat
  `SEAT_PRICE = 14`. Backend is authoritative; the flat constant goes away in Part 7.
- TECH.md §6 has no `seat_type` pricing multiplier even though `seats.seat_type` exists —
  treated as a display-only label for v1.
- Booking reference (`LUM-77291`) is client-generated in the handoff. Moves server-side in
  Part 5 so it can be unique and looked up.
- Showing day boundaries are UTC (Part 3). Correct while the whole stack runs UTC, but a real
  chain needs the *venue's* local day — otherwise a 00:30 screening lands on the previous day's
  listing. Would mean a timezone column on `venues`.
- Seeded showings are positioned relative to `CURDATE()` at first migration run. Flyway applies
  a migration once, so the seeded week does not roll forward — after a few days the "Today"
  chip goes empty until showings are added through the Part 3 admin screens (or the volume is
  recreated with `docker compose down -v`).
