package com.cinema.seat.event;

import com.cinema.booking.repository.BookingRepository;
import com.cinema.seat.domain.HoldKey;
import com.cinema.seat.domain.SeatStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * TECH.md §5 step 4: a hold that is never confirmed lapses, and the seat has to go back to
 * available for everyone watching — without anybody polling and without a scheduled sweep.
 *
 * <p>Redis publishes {@code __keyevent@0__:expired} when a TTL key is removed, which
 * {@link KeyExpirationEventMessageListener} subscribes to. The message body is the <em>key
 * name only</em>; the value is already gone, which is why {@link HoldKey} encodes the showing
 * and seat ids in the key itself.
 *
 * <p>Two properties worth knowing before trusting this:
 * <ul>
 *   <li>The event fires when Redis actually deletes the key, which is on access or during its
 *       periodic sweep — near the TTL, not exactly on it.</li>
 *   <li>Keyspace notifications are plain pub/sub with no delivery guarantee: a listener that is
 *       disconnected at the wrong moment misses the event outright. So this is an optimisation
 *       for open seat maps, never the source of truth — {@code GET /seat-map} recomputes status
 *       from Redis and MySQL on every read, and never from accumulated broadcasts.</li>
 * </ul>
 */
@Component
@Slf4j
public class SeatHoldExpiryListener extends KeyExpirationEventMessageListener {

    private final SeatEventPublisher events;
    private final BookingRepository bookings;

    public SeatHoldExpiryListener(RedisMessageListenerContainer container,
                                  SeatEventPublisher events,
                                  BookingRepository bookings) {
        super(container);
        this.events = events;
        this.bookings = bookings;
        // Matches the `--notify-keyspace-events Ex` in docker-compose.yml. Spring only applies
        // this when the server has notifications switched off entirely, so a deployment that has
        // already configured them keeps its own setting.
        setKeyspaceNotificationsConfigParameter("Ex");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = new String(message.getBody(), StandardCharsets.UTF_8);
        HoldKey.parse(key).ifPresent(this::announce);
    }

    private void announce(HoldKey key) {
        // A sale deletes the hold key, and DEL fires no expiry event, so reaching here normally
        // means the seat really is free again. The exception is a sale landing in the same
        // instant the TTL lapses — checking the database costs one indexed lookup and stops the
        // room being told a sold seat is available.
        SeatStatus status = bookings.existsByShowingIdAndSeatId(key.showingId(), key.seatId())
                ? SeatStatus.SOLD
                : SeatStatus.AVAILABLE;

        log.debug("Hold expired on showing {} seat {} -> {}", key.showingId(), key.seatId(), status);
        events.publish(key.showingId(), key.seatId(), status);
    }
}
