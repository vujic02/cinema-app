# Cinema Seat Booking System — Tech Brief

## 1. Project Overview

A cinema ticketing platform where customers browse showings, pick seats on a
visual seat map, and complete a simulated purchase. Admins manage venues,
seat layouts, movies, and showings through a separate dashboard.

The flagship technical feature is **seat-hold concurrency**: when a seat is
selected, it's temporarily reserved so two customers can't book the same
seat at once, with real-time updates pushed to everyone viewing that showing.

## 2. User Roles

| Role | Capabilities |
|---|---|
| `ADMIN` | Manage venues, seat layouts, movies, showings, view all bookings, view platform analytics |
| `CUSTOMER` | Browse showings, select seats, complete simulated checkout, view own booking history |

> Assumption: starting with two roles (Admin, Customer) to keep v1 scope
> tight. A `MANAGER` role (e.g. venue-scoped admin) can be added later
> without major rework — it would just get a narrower set of permissions
> than `ADMIN`.

## 3. Tech Stack

### Backend
- **Language / Framework:** Java, Spring Boot
- **Auth:** Spring Security + JWT (short-lived access token + refresh token)
- **Validation:** Jakarta Bean Validation
- **Real-time:** WebSocket via STOMP over SockJS (live seat status pushes)
- **DB access:** Spring Data JPA
- **Migrations:** Flyway (versioned SQL migration files, no `ddl-auto`)
- **API docs:** springdoc-openapi (Swagger UI)
- **Seat hold TTL:** Redis (`SET key value EX 300` style holds, auto-expire)
- **Testing:** JUnit 5 + Testcontainers (tests run against a real MySQL instance)

### Frontend
- **Framework:** React
- **Styling:** Tailwind CSS
- **Data fetching / caching:** TanStack Query (React Query)
- **State management:** React Context (no external state library)
- **Real-time:** WebSocket client (SockJS + STOMP client) for live seat updates
- **Production serving:** built to static files (`npm run build`), served by Nginx

### Database
- **MySQL**, schema managed entirely through Flyway migrations
- **Access:** Spring Data JPA / Hibernate — see §3a for the detailed pattern

### 3a. Database Access Pattern

Spring Data JPA / Hibernate over MySQL, following a tiered repository
approach (scaled down from a larger prior project with the same stack):

- `spring-boot-starter-data-jpa`, `ddl-auto=validate` (schema is owned by
  Flyway, Hibernate only validates it matches — never `update`)
- `spring.jpa.open-in-view=false` — forces fetching what's needed inside
  the service layer instead of lazy-loading in the controller
- `mysql-connector-j` driver, HikariCP pool, server-side prepared
  statement caching on
- Flyway migrations under `classpath:db/migration`, timestamp-named
- No JDBC template, no MyBatis, no raw SQL outside migrations

**Repo pattern** — tiered by need, not applied uniformly:
1. **Derived query methods** for the common case
   (`findByShowingIdAndStatus`, `findByUserIdOrderByCreatedAtDesc`)
2. **`@Query` JPQL** (text blocks + `@Param`) for aggregates and fetch
   joins — e.g. the seat-map query that loads a showing's seats plus
   current booking status in one round trip, avoiding N+1
3. **`JpaSpecificationExecutor` + Specifications** (`BookingSpecifications`,
   `ShowingSpecifications`) for admin screens with dynamic filters
   (date range, venue, status) — only introduced where filtering is
   genuinely dynamic
4. **Projection interfaces** for dashboard counters
   (`BookingsPerShowingView`, revenue-by-date aggregates)

**No Spring Session JDBC.** This project uses stateless JWT auth (see
§Auth below), not cookie/session-based auth, so there's no server-side
session store to persist. Session-based auth fits a Thymeleaf-style
monolith where the server renders pages directly to the browser; it
doesn't fit a separate React client talking to an API.

**Refresh token revocation:** pure stateless JWT can't be revoked before
it expires, so refresh tokens are tracked in their own table/repo instead
of a full session store:

```java
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);
    void deleteAllByUserId(Long userId); // "log out everywhere"
}
```

### Infrastructure
- **Containerization:** Docker
- **Orchestration (local + prod):** Docker Compose — services: `frontend` (Nginx), `backend` (Spring Boot), `mysql`, `redis`
- **CI:** GitHub Actions — one workflow runs backend tests (with Testcontainers), one runs frontend build/lint, both triggered on push/PR

### Payments
- **Simulated only.** No real payment gateway. Checkout is a "confirm purchase" action that transitions held seats to `sold` and creates a booking record. No Stripe/PayPal integration in v1.

## 4. Why Nginx for the Frontend

`npm run build` compiles React into static HTML/JS/CSS — no Node.js needed
at runtime. Nginx serves those static files and additionally:
1. Redirects unmatched routes back to `index.html` so React Router's
   client-side routing survives page refreshes.
2. Reverse-proxies `/api/*` requests to the Spring Boot backend container,
   so the browser only talks to one origin and CORS isn't an issue.

## 5. Core Feature: Seat Hold Concurrency

This is the feature the whole project is designed to showcase — treat it
as the most important thing to get right.

**Flow:**
1. Customer opens a showing → seat map loads via REST, current seat
   statuses (`available` / `held` / `sold`) come back from MySQL.
2. Customer clicks a seat → backend attempts to place a hold:
   - Write a Redis key like `hold:{showingId}:{seatId}` with a TTL (e.g. 5 min)
   - Reject if a hold or a sold record already exists for that seat
3. On successful hold, broadcast a WebSocket message to everyone viewing
   that showing: `{ seatId, status: 'held' }`
4. If the hold expires (Redis TTL fires) without confirmation, the seat
   reverts to `available` — broadcast that change too.
5. On "confirm purchase," the backend validates the hold still belongs to
   that user/session, writes a `sold` record to MySQL inside a transaction,
   deletes the Redis hold key, and broadcasts `{ seatId, status: 'sold' }`.

**Edge case to handle explicitly:** two requests racing to hold the same
seat within milliseconds of each other. The Redis write (or a MySQL unique
constraint as a backstop) must guarantee only one wins.

## 6. Data Model (starting point)

```
venues        (id, name, address)
rows          (id, venue_id, row_number, seat_count)
seats         (id, row_id, seat_number, seat_type)
movies        (id, title, duration_minutes, description)
showings      (id, movie_id, venue_id, start_time, price)
bookings      (id, showing_id, seat_id, user_id, status['held','sold'], created_at)
users         (id, email, password_hash, role)
```

Seat layout for the initial venue: row 1 = 4 seats, rows 2–4 = 6 seats
each, row 5 = 7 seats — matching the reference seat-map design (aisle gaps
allowed per row via a `is_aisle_gap` flag on individual seats).

## 7. Non-Goals for v1

- Real payment processing
- Multi-venue chains / franchise management
- Microservices split (this stays a single Spring Boot app, package-by-feature)
- Mobile app (web-responsive only)
- Email notifications (nice-to-have, not core)

## 8. Suggested Project Structure

```
/backend
  /src/main/java/com/cinema
    /auth
    /venue
    /booking
    /showing
    /config
  /src/main/resources
    /db/migration       # Flyway SQL files
  /src/test

/frontend
  /src
    /components
    /pages
    /context
    /hooks
    /api

/docker-compose.yml
/docker-compose.prod.yml
/.github/workflows
  backend-ci.yml
  frontend-ci.yml
```
