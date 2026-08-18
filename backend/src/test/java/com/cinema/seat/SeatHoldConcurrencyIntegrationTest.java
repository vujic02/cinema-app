package com.cinema.seat;

import com.cinema.common.exception.ApiException;
import com.cinema.seat.domain.HoldKey;
import com.cinema.seat.service.SeatHoldStore;
import com.cinema.seat.service.SeatService;
import com.cinema.support.SeatBroadcasts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The edge case TECH.md §5 calls out by name: "two requests racing to hold the same seat within
 * milliseconds of each other… only one wins."
 *
 * <p>Driven through {@link SeatService} rather than MockMvc so the threads genuinely collide on
 * the Redis {@code SET NX} instead of on servlet dispatch, and so the loser's exception can be
 * inspected rather than only its status code.
 */
class SeatHoldConcurrencyIntegrationTest extends AbstractSeatIntegrationTest {

    private static final int RACERS = 24;

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatHoldStore holds;

    @Test
    @DisplayName("24 users race for one seat and exactly one of them gets it")
    void exactlyOneRacerWins() throws Exception {
        Auditorium hall = auditorium();
        long seatId = hall.seat(0);

        AtomicInteger won = new AtomicInteger();
        AtomicInteger lost = new AtomicInteger();
        AtomicLong winner = new AtomicLong();
        List<String> unexpected = new ArrayList<>();

        // Every racer needs a distinct user id: two threads sharing one would both be told
        // ALREADY_YOURS, which is a success, and the count would lie. The ids are synthetic —
        // hold() takes the caller's id from a verified token and never re-checks the user table.
        try (ExecutorService pool = Executors.newFixedThreadPool(RACERS);
             SeatBroadcasts broadcasts = new SeatBroadcasts(brokerChannel)) {

            CyclicBarrier gate = new CyclicBarrier(RACERS);
            List<Future<Void>> attempts = new ArrayList<>();

            for (int i = 0; i < RACERS; i++) {
                long userId = 9_000L + i;
                Callable<Void> attempt = () -> {
                    // Every thread parks here until the last one arrives, so the requests land
                    // together rather than spread out by thread startup.
                    gate.await(10, TimeUnit.SECONDS);
                    try {
                        seatService.hold(hall.showingId(), seatId, userId);
                        won.incrementAndGet();
                        winner.set(userId);
                    } catch (ApiException ex) {
                        lost.incrementAndGet();
                        if (!"SEAT_HELD".equals(ex.getCode())) {
                            synchronized (unexpected) {
                                unexpected.add(ex.getCode());
                            }
                        }
                    }
                    return null;
                };
                attempts.add(pool.submit(attempt));
            }

            for (Future<Void> attempt : attempts) {
                attempt.get(30, TimeUnit.SECONDS);
            }

            assertThat(won.get()).isOne();
            assertThat(lost.get()).isEqualTo(RACERS - 1);
            // Everyone who lost was told why, in the one way the UI knows how to handle.
            assertThat(unexpected).isEmpty();

            // Redis agrees with the winner.
            assertThat(holds.holderOf(new HoldKey(hall.showingId(), seatId))).contains(winner.get());

            // And the room heard about it. Awaited rather than drained: the broker channel
            // delivers on its own executor, so the message is not on the queue the instant
            // hold() returns. Only one broadcast is ever published here — losers publish
            // nothing — so the queue is empty behind this one.
            SeatBroadcasts.Broadcast sent = broadcasts.awaitNext(Duration.ofSeconds(5));
            assertThat(sent.destination()).isEqualTo("/topic/showings/" + hall.showingId());
            assertThat(sent.payload()).contains("\"status\":\"HELD\"");
            assertThat(broadcasts.drain()).isEmpty();
        }
    }

    @Test
    @DisplayName("racing for different seats never blocks: everyone gets their own")
    void distinctSeatsAllSucceed() throws Exception {
        Auditorium hall = auditorium();
        int seatCount = hall.seatIds().size();

        AtomicInteger won = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(seatCount)) {
            CyclicBarrier gate = new CyclicBarrier(seatCount);
            List<Future<Void>> attempts = new ArrayList<>();

            for (int i = 0; i < seatCount; i++) {
                long seatId = hall.seat(i);
                long userId = 8_000L + i;
                attempts.add(pool.submit(() -> {
                    gate.await(10, TimeUnit.SECONDS);
                    seatService.hold(hall.showingId(), seatId, userId);
                    won.incrementAndGet();
                    return null;
                }));
            }
            for (Future<Void> attempt : attempts) {
                attempt.get(30, TimeUnit.SECONDS);
            }
        }

        assertThat(won.get()).isEqualTo(seatCount);
        assertThat(holds.holdsForShowing(hall.showingId())).hasSize(seatCount);
        assertThat(seatService.seatMap(hall.showingId(), null).availableCount()).isZero();
    }

    @Test
    @DisplayName("the loser of a race gets the seat once the winner releases it")
    void seatIsReusableAfterRelease() throws Exception {
        Auditorium hall = auditorium();
        long seatId = hall.seat(1);

        seatService.hold(hall.showingId(), seatId, 7_001L);
        assertThat(holds.holderOf(new HoldKey(hall.showingId(), seatId))).contains(7_001L);

        seatService.release(hall.showingId(), seatId, 7_001L);
        seatService.hold(hall.showingId(), seatId, 7_002L);

        assertThat(holds.holderOf(new HoldKey(hall.showingId(), seatId))).contains(7_002L);
    }

    @Test
    @DisplayName("a hold placed then read back reports a TTL inside the configured window")
    void holdCarriesTtl() throws Exception {
        Auditorium hall = auditorium();

        var response = seatService.hold(hall.showingId(), hall.seat(2), 7_100L);

        assertThat(response.expiresInSeconds()).isEqualTo(300);
        assertThat(response.expiresAt()).isAfter(java.time.Instant.now().plus(Duration.ofSeconds(290)));
    }
}
