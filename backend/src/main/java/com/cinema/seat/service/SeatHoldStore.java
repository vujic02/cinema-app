package com.cinema.seat.service;

import com.cinema.seat.domain.HoldKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every Redis operation seat holds need, and nothing else — no validation, no HTTP status codes,
 * no broadcasting. {@link SeatService} owns those; Part 5's checkout leans on this class directly
 * to confirm a hold is still the buyer's before writing the sale.
 *
 * <p><b>Why this is the race-safe layer.</b> TECH.md §5's edge case is two requests arriving
 * milliseconds apart for the same seat. {@code SET key value NX EX ttl} is a single Redis command
 * and Redis executes commands one at a time, so exactly one of them can find the key absent.
 * There is no read-then-write window for the second request to slip into.
 */
@Component
@RequiredArgsConstructor
public class SeatHoldStore {

    /**
     * Release has to be compare-and-delete, not delete: between reading the holder and deleting
     * the key, that hold could expire and another user could take the seat — a plain {@code DEL}
     * would then throw away a stranger's hold. A script runs as one atomic unit, so the value
     * checked is the value deleted.
     *
     * <p>Returns 1 released, 0 held by someone else, -1 no hold at all.
     */
    private static final RedisScript<Long> RELEASE_IF_MINE = new DefaultRedisScript<>("""
            local holder = redis.call('GET', KEYS[1])
            if holder == false then return -1 end
            if holder == ARGV[1] then
              redis.call('DEL', KEYS[1])
              return 1
            end
            return 0
            """, Long.class);

    /** Keys per SCAN round trip. A full auditorium is ~30 seats, so one pass covers a showing. */
    private static final int SCAN_BATCH = 256;

    private final StringRedisTemplate redis;

    /**
     * Attempts the hold. The {@code NX} is the whole feature: the loser of a tie is told no,
     * rather than quietly overwriting the winner.
     */
    public HoldResult acquire(HoldKey key, long userId, Duration ttl) {
        String redisKey = key.format();
        String me = String.valueOf(userId);

        if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(redisKey, me, ttl))) {
            return new HoldResult(HoldResult.Outcome.ACQUIRED, ttl);
        }

        // Lost the NX. Read who won — but the hold can lapse in the gap between the two
        // commands, in which case the seat is free again and this caller should get it.
        String holder = redis.opsForValue().get(redisKey);
        if (holder == null) {
            if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(redisKey, me, ttl))) {
                return new HoldResult(HoldResult.Outcome.ACQUIRED, ttl);
            }
            holder = redis.opsForValue().get(redisKey);
        }

        if (me.equals(holder)) {
            return new HoldResult(HoldResult.Outcome.ALREADY_YOURS, remaining(redisKey));
        }
        // Only one retry: a seat whose hold keeps churning this fast is genuinely contested, and
        // looping here would turn a hot seat into a spin on the Redis connection.
        return new HoldResult(HoldResult.Outcome.HELD_BY_OTHER, remaining(redisKey));
    }

    public ReleaseOutcome release(HoldKey key, long userId) {
        Long result = redis.execute(RELEASE_IF_MINE, List.of(key.format()), String.valueOf(userId));
        if (result == null || result == -1L) {
            return ReleaseOutcome.NOT_HELD;
        }
        return result == 1L ? ReleaseOutcome.RELEASED : ReleaseOutcome.NOT_YOURS;
    }

    /** Who currently holds this seat, if anyone. The check Part 5's checkout runs. */
    public Optional<Long> holderOf(HoldKey key) {
        String holder = redis.opsForValue().get(key.format());
        return holder == null ? Optional.empty() : Optional.of(Long.valueOf(holder));
    }

    /** Deletes a hold whoever owns it — for the sale path, which supersedes the hold entirely. */
    public boolean forceRelease(HoldKey key) {
        return Boolean.TRUE.equals(redis.delete(key.format()));
    }

    /**
     * Every live hold on a showing, as {@code seatId -> holderUserId}.
     *
     * <p>{@code SCAN}, not {@code KEYS}: {@code KEYS} walks the entire keyspace in one blocking
     * pass and stalls every other client while it runs — including the {@code SET NX} that the
     * seat race depends on. {@code SCAN} is cursor-based and yields between batches.
     */
    public Map<Long, Long> holdsForShowing(long showingId) {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(HoldKey.showingPattern(showingId))
                .count(SCAN_BATCH)
                .build();

        try (Cursor<String> cursor = redis.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (keys.isEmpty()) {
            return Map.of();
        }

        // One MGET rather than a GET per key. Values can come back null: a hold is free to
        // expire between the SCAN and this read, and a seat that just went free is simply
        // absent from the map.
        List<String> holders = redis.opsForValue().multiGet(keys);
        Map<Long, Long> heldBy = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            String holder = holders == null ? null : holders.get(i);
            if (holder == null) {
                continue;
            }
            HoldKey.parse(keys.get(i))
                    .ifPresent(parsed -> heldBy.put(parsed.seatId(), Long.valueOf(holder)));
        }
        return heldBy;
    }

    private Duration remaining(String redisKey) {
        Long seconds = redis.getExpire(redisKey);
        // -2 is "no such key", -1 is "no TTL"; neither is time the caller can count down.
        return seconds == null || seconds < 0 ? Duration.ZERO : Duration.ofSeconds(seconds);
    }
}
