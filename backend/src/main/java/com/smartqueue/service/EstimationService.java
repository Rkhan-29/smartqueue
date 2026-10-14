package com.smartqueue.service;

import com.smartqueue.dto.QueueDtos.EstimateConfidence;
import com.smartqueue.entity.QueueConfiguration;
import com.smartqueue.repository.CounterRepository;
import com.smartqueue.repository.QueueTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wait-time estimation: people ahead x average service time, divided across active counters.
 * The configured average is blended with recent history as data accumulates. Results are estimates only.
 */
@Service
@RequiredArgsConstructor
public class EstimationService {

    private static final long CACHE_TTL_MILLIS = 30_000L;
    private static final Duration HISTORY_WINDOW = Duration.ofDays(14);
    private static final int MIN_SAMPLES = 5;
    private static final int FULL_WEIGHT_SAMPLES = 30;
    private static final double MIN_AVG_MINUTES = 0.5;
    private static final double MAX_AVG_MINUTES = 240.0;

    public record QueueEstimate(double avgServiceMinutes, int servers, EstimateConfidence confidence) {
        public int minutesFor(long peopleAhead) {
            if (peopleAhead <= 0) {
                return 0;
            }
            return (int) Math.ceil(peopleAhead * avgServiceMinutes / Math.max(1, servers));
        }
    }

    private record Cached(QueueEstimate estimate, long cachedAtMillis) {
    }

    private final QueueTokenRepository tokens;
    private final CounterRepository counters;
    private final ConcurrentHashMap<UUID, Cached> cache = new ConcurrentHashMap<>();

    public QueueEstimate estimate(UUID queueId, QueueConfiguration cfg) {
        long now = System.currentTimeMillis();
        Cached cached = cache.get(queueId);
        if (cached != null && now - cached.cachedAtMillis() < CACHE_TTL_MILLIS) {
            return cached.estimate();
        }
        QueueEstimate fresh = compute(queueId, cfg);
        cache.put(queueId, new Cached(fresh, now));
        return fresh;
    }

    public void evict(UUID queueId) {
        cache.remove(queueId);
    }

    private QueueEstimate compute(UUID queueId, QueueConfiguration cfg) {
        double configured = cfg.getAvgServiceMinutes();
        Instant since = Instant.now().minus(HISTORY_WINDOW);
        long samples = tokens.completedSampleCount(queueId, since);
        double avg = configured;
        EstimateConfidence confidence = EstimateConfidence.LOW;

        if (samples >= MIN_SAMPLES) {
            Double historicalSeconds = tokens.averageServiceSeconds(queueId, since);
            if (historicalSeconds != null) {
                double historical = Math.max(MIN_AVG_MINUTES, Math.min(MAX_AVG_MINUTES, historicalSeconds / 60.0));
                double weight = Math.min(1.0, samples / (double) FULL_WEIGHT_SAMPLES);
                avg = weight * historical + (1 - weight) * configured;
                confidence = samples >= FULL_WEIGHT_SAMPLES ? EstimateConfidence.HIGH : EstimateConfidence.MEDIUM;
            }
        }
        int servers = (int) Math.max(1, counters.countByQueueIdAndActiveTrue(queueId));
        return new QueueEstimate(avg, servers, confidence);
    }
}
