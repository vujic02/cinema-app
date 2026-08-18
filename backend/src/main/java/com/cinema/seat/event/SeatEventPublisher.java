package com.cinema.seat.event;

import com.cinema.seat.domain.SeatStatus;
import com.cinema.seat.dto.SeatStatusEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * The one place that writes to {@code /topic/showings/{id}} (TECH.md §5, steps 3-5). Keeping it
 * behind a component means services never touch {@link SimpMessagingTemplate}, and the topic
 * naming lives in a single spot the frontend hook in Part 8 can be checked against.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeatEventPublisher {

    public static final String TOPIC_PREFIX = "/topic/showings/";

    private final SimpMessagingTemplate messaging;

    public void publish(long showingId, long seatId, SeatStatus status) {
        String destination = TOPIC_PREFIX + showingId;
        log.debug("Broadcasting {} seat {} -> {}", destination, seatId, status);
        messaging.convertAndSend(destination, new SeatStatusEvent(showingId, seatId, status));
    }
}
