package com.cinema.support;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.AbstractSubscribableChannel;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Captures what the app broadcasts to STOMP subscribers, by subscribing a plain handler to the
 * broker channel that {@code SimpMessagingTemplate} publishes on.
 * <p>
 * Preferred over mocking the publisher: this exercises the real path — the destination string,
 * the JSON converter, the whole message — and needs no {@code @MockitoSpyBean}, so the Spring
 * context is not marked dirty and the suite keeps sharing one.
 * <p>
 * Payloads arrive already converted to JSON bytes: conversion happens inside
 * {@code SimpMessagingTemplate} before the message reaches this channel.
 */
public final class SeatBroadcasts implements MessageHandler, AutoCloseable {

    public record Broadcast(String destination, String payload) {
    }

    private final AbstractSubscribableChannel brokerChannel;
    private final BlockingQueue<Broadcast> received = new LinkedBlockingQueue<>();

    public SeatBroadcasts(AbstractSubscribableChannel brokerChannel) {
        this.brokerChannel = brokerChannel;
        brokerChannel.subscribe(this);
    }

    @Override
    public void handleMessage(Message<?> message) {
        String destination = (String) message.getHeaders().get(SimpMessageHeaderAccessor.DESTINATION_HEADER);
        Object payload = message.getPayload();
        String body = payload instanceof byte[] bytes
                ? new String(bytes, StandardCharsets.UTF_8)
                : String.valueOf(payload);
        received.add(new Broadcast(destination, body));
    }

    /** Blocks for the next broadcast, failing rather than returning null if none arrives. */
    public Broadcast awaitNext(Duration timeout) throws InterruptedException {
        Broadcast next = received.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (next == null) {
            throw new AssertionError("No broadcast arrived within " + timeout);
        }
        return next;
    }

    /**
     * Blocks for the first broadcast matching {@code predicate}, discarding the ones before it.
     * <p>
     * Needed wherever the interesting event is not the first: a hold that is about to expire has
     * to be placed <em>before</em> subscribing would be safe, so the HELD frame is already in the
     * queue ahead of the expiry the test is actually waiting on.
     */
    public Broadcast awaitMatching(Predicate<Broadcast> predicate, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<Broadcast> skipped = new ArrayList<>();

        while (System.nanoTime() < deadline) {
            long remaining = Math.max(0, deadline - System.nanoTime());
            Broadcast next = received.poll(remaining, TimeUnit.NANOSECONDS);
            if (next == null) {
                break;
            }
            if (predicate.test(next)) {
                return next;
            }
            skipped.add(next);
        }
        throw new AssertionError("No matching broadcast within " + timeout + "; saw " + skipped);
    }

    public List<Broadcast> drain() {
        List<Broadcast> all = new ArrayList<>();
        received.drainTo(all);
        return all;
    }

    public void clear() {
        received.clear();
    }

    @Override
    public void close() {
        brokerChannel.unsubscribe(this);
    }
}
