package com.smartqueue.service;

import com.smartqueue.entity.QueueConfiguration;
import com.smartqueue.entity.QueueToken;
import com.smartqueue.entity.TokenStatus;
import com.smartqueue.repository.QueueConfigurationRepository;
import com.smartqueue.repository.QueueTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Background housekeeping: marks called-but-absent tokens as no-show and expires stale waiting tokens.
 * Each token is handled in its own transaction (via {@link QueueEngine}) so one failure never blocks others.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueueMaintenanceService {

    private final QueueTokenRepository tokens;
    private final QueueConfigurationRepository configs;
    private final QueueEngine engine;

    @Scheduled(fixedDelayString = "${smartqueue.maintenance.interval-ms:30000}", initialDelay = 30_000)
    public void run() {
        try {
            expireNoShows();
            expireStaleWaiting();
        } catch (RuntimeException e) {
            log.error("Queue maintenance run failed", e);
        }
    }

    void expireNoShows() {
        Instant now = Instant.now();
        Map<UUID, QueueConfiguration> cfgByQueue = new HashMap<>();
        for (QueueConfiguration c : configs.findAllWithActiveQueue()) {
            cfgByQueue.put(c.getQueue().getId(), c);
        }
        for (QueueToken t : tokens.findByStatus(TokenStatus.CALLED)) {
            QueueConfiguration cfg = cfgByQueue.get(t.getQueue().getId());
            if (cfg == null || t.getCalledAt() == null) {
                continue;
            }
            if (t.getCalledAt().plus(Duration.ofMinutes(cfg.getNoShowTimeoutMinutes())).isBefore(now)) {
                try {
                    engine.autoMarkNoShow(t.getId());
                } catch (RuntimeException e) {
                    log.warn("Could not mark token {} as no-show: {}", t.getId(), e.getMessage());
                }
            }
        }
    }

    void expireStaleWaiting() {
        Instant now = Instant.now();
        for (QueueConfiguration cfg : configs.findAllWithActiveQueue()) {
            Instant cutoff = now.minus(Duration.ofMinutes(cfg.getAutoExpireMinutes()));
            List<UUID> stale = tokens.findIdsOlderThan(cfg.getQueue().getId(), TokenStatus.WAITING, cutoff);
            for (UUID id : stale) {
                try {
                    engine.autoExpire(id);
                } catch (RuntimeException e) {
                    log.warn("Could not expire token {}: {}", id, e.getMessage());
                }
            }
        }
    }
}
