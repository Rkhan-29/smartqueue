package com.smartqueue.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Small fixed-window, in-memory rate limiter. Adequate for a single instance; behind multiple
 * instances, enforce limits at the gateway/load balancer as well (or swap for a Redis-backed one).
 */
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(long startMillis, AtomicInteger count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /** @return true if the call is allowed, false if the limit for the current window is exceeded. */
    public boolean tryAcquire(String key, int limit, long windowMillis) {
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startMillis() >= windowMillis) {
                return new Window(now, new AtomicInteger(0));
            }
            return existing;
        });
        boolean allowed = w.count().incrementAndGet() <= limit;
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(e -> now - e.getValue().startMillis() >= windowMillis);
        }
        return allowed;
    }
}
