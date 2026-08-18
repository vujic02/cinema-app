package com.cinema.seat;

import com.cinema.seat.domain.HoldKey;
import com.cinema.seat.service.SeatHoldStore;
import com.cinema.seat.service.SeatService;
import com.cinema.support.SeatBroadcasts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * TECH.md §5 step 4: a hold nobody confirms lapses on its own, the seat goes back to available,
 * and the change is pushed to everyone watching — with no polling and no scheduled sweep.
 *
 * <p>Runs with a one-second TTL, which is why it is its own class: the property override gives
 * it a separate application context, while the MySQL and Redis containers stay shared.
 *
 * <p>Every test subscribes <em>before</em> placing its hold and then waits for the frame it cares
 * about. At this TTL the key can lapse while the test is still setting up, so subscribing
 * afterwards would race the very event being asserted on.
 */
@TestPropertySource(properties = "app.seat-hold.ttl-seconds=1")
class SeatHoldExpiryIntegrationTest extends AbstractSeatIntegrationTest {

    /** Redis sweeps expired keys ten times a second, so the event lands close behind the TTL. */
    private static final Duration EXPIRY_WINDOW = Duration.ofSeconds(20);

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatHoldStore holds;

    @Test
    @DisplayName("an unconfirmed hold expires, frees the seat, and broadcasts AVAILABLE")
    void holdExpiresAndBroadcasts() throws Exception {
        Auditorium hall = auditorium();
        long seatId = hall.seat(0);
        long userId = 6_001L;

        try (SeatBroadcasts broadcasts = new SeatBroadcasts(brokerChannel)) {
            var response = seatService.hold(hall.showingId(), seatId, userId);
            assertThat(response.expiresInSeconds()).isEqualTo(1);

            var expired = broadcasts.awaitMatching(
                    frame -> frame.payload().contains("\"status\":\"AVAILABLE\""), EXPIRY_WINDOW);

            assertThat(expired.destination()).isEqualTo("/topic/showings/" + hall.showingId());
            assertThat(objectMapper.readTree(expired.payload()).get("seatId").asLong()).isEqualTo(seatId);
        }

        assertThat(holds.holderOf(new HoldKey(hall.showingId(), seatId))).isEmpty();
        assertThat(seatService.seatMap(hall.showingId(), userId).availableCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("a seat someone let lapse can be taken by the next user")
    void expiredSeatIsHoldableAgain() throws Exception {
        Auditorium hall = auditorium();
        long seatId = hall.seat(1);

        seatService.hold(hall.showingId(), seatId, 6_101L);

        // Poll the store rather than sleep a fixed amount: the TTL is the floor, the sweep
        // decides the actual moment.
        await().atMost(EXPIRY_WINDOW)
                .pollInterval(Duration.ofMillis(100))
                .until(() -> holds.holderOf(new HoldKey(hall.showingId(), seatId)).isEmpty());

        seatService.hold(hall.showingId(), seatId, 6_102L);
        assertThat(holds.holderOf(new HoldKey(hall.showingId(), seatId))).contains(6_102L);
    }

    @Test
    @DisplayName("a hold that expires after the seat was sold broadcasts SOLD, not AVAILABLE")
    void expiryDoesNotResurrectASoldSeat() throws Exception {
        Auditorium hall = auditorium();
        long seatId = hall.seat(2);

        // Stands in for a checkout that outran its own cleanup: the sale is committed, but the
        // hold key is left to lapse on its own rather than being deleted. Written in this order,
        // and through the store rather than the service, so the booking is unambiguously in
        // place before the TTL can fire — the service would refuse to hold a sold seat, and
        // selling after the hold would race the one-second expiry this class runs with.
        sell(hall.showingId(), seatId);

        try (SeatBroadcasts broadcasts = new SeatBroadcasts(brokerChannel)) {
            holds.acquire(new HoldKey(hall.showingId(), seatId), 6_201L, Duration.ofSeconds(1));

            // Only the expiry listener broadcasts here, and without its database check the room
            // would be told a sold seat is free again.
            var frame = broadcasts.awaitNext(EXPIRY_WINDOW);
            var payload = objectMapper.readTree(frame.payload());

            assertThat(payload.get("seatId").asLong()).isEqualTo(seatId);
            assertThat(payload.get("status").asText()).isEqualTo("SOLD");
        }
    }
}
