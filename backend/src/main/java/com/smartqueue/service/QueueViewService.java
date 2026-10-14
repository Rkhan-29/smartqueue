package com.smartqueue.service;

import com.smartqueue.dto.QueueDtos.CounterDto;
import com.smartqueue.dto.QueueDtos.QueueSnapshot;
import com.smartqueue.dto.QueueDtos.QueueStatsDto;
import com.smartqueue.dto.QueueDtos.ServingEntry;
import com.smartqueue.dto.QueueDtos.StaffQueueState;
import com.smartqueue.dto.QueueDtos.StaffTokenDto;
import com.smartqueue.dto.QueueDtos.TokenView;
import com.smartqueue.entity.Branch;
import com.smartqueue.entity.Counter;
import com.smartqueue.entity.PriorityMode;
import com.smartqueue.entity.QueueConfiguration;
import com.smartqueue.entity.QueueStatus;
import com.smartqueue.entity.QueueToken;
import com.smartqueue.entity.ServiceQueue;
import com.smartqueue.entity.TokenStatus;
import com.smartqueue.exception.ApiException;
import com.smartqueue.exception.ErrorCode;
import com.smartqueue.repository.CounterRepository;
import com.smartqueue.repository.QueueConfigurationRepository;
import com.smartqueue.repository.QueueTokenRepository;
import com.smartqueue.repository.ServiceQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Read-side assembly of queue snapshots, customer token views and staff dashboards. */
@Service
@RequiredArgsConstructor
public class QueueViewService {

    private static final EnumSet<TokenStatus> IN_PROGRESS = EnumSet.of(TokenStatus.CALLED, TokenStatus.SERVING);
    private static final EnumSet<TokenStatus> FINISHED = EnumSet.of(TokenStatus.COMPLETED, TokenStatus.CANCELLED,
            TokenStatus.SKIPPED, TokenStatus.NO_SHOW);
    private static final int RECENT_LIMIT = 15;

    private final QueueTokenRepository tokens;
    private final ServiceQueueRepository queues;
    private final QueueConfigurationRepository configs;
    private final CounterRepository counters;
    private final EstimationService estimation;

    // ------------------------------------------------------------------ availability

    /** Returns the reason a new token cannot currently be issued, or null if joining is possible. */
    public ErrorCode joinBlocker(ServiceQueue q, QueueConfiguration cfg, long waitingCount) {
        if (q.getStatus() == QueueStatus.CLOSED) {
            return ErrorCode.QUEUE_CLOSED;
        }
        if (q.getStatus() == QueueStatus.PAUSED) {
            return ErrorCode.QUEUE_PAUSED;
        }
        var now = QueueTime.nowTime(q);
        if (!QueueTime.withinWorkingHours(cfg, now)) {
            return ErrorCode.OUTSIDE_WORKING_HOURS;
        }
        if (QueueTime.onBreak(cfg, now)) {
            return ErrorCode.QUEUE_ON_BREAK;
        }
        if (waitingCount >= cfg.getMaxQueueSize()) {
            return ErrorCode.QUEUE_FULL;
        }
        return null;
    }

    // ------------------------------------------------------------------ snapshot

    @Transactional(readOnly = true)
    public QueueSnapshot snapshot(UUID queueId) {
        ServiceQueue q = tokensQueue(queueId);
        return snapshot(q, requireConfig(queueId));
    }

    public QueueSnapshot snapshot(ServiceQueue q, QueueConfiguration cfg) {
        UUID queueId = q.getId();
        long waiting = tokens.countByQueueIdAndStatus(queueId, TokenStatus.WAITING);
        List<QueueToken> active = tokens.findByQueueIdAndStatusInOrderByCalledAtDesc(queueId, IN_PROGRESS);
        List<ServingEntry> serving = active.stream()
                .map(t -> new ServingEntry(t.getTokenNumber(),
                        t.getCounter() == null ? null : t.getCounter().getName(), t.getStatus()))
                .toList();
        String lastCalled = !active.isEmpty() ? active.get(0).getTokenNumber()
                : tokens.findFirstByQueueIdAndServiceDateAndCalledAtIsNotNullOrderByCalledAtDesc(
                        queueId, QueueTime.today(q)).map(QueueToken::getTokenNumber).orElse(null);

        var estimate = estimation.estimate(queueId, cfg);
        ErrorCode blocker = joinBlocker(q, cfg, waiting);
        Branch branch = q.getService().getDepartment().getBranch();
        return new QueueSnapshot(
                queueId, q.getPublicRef(), q.getName(), q.getStatus(),
                q.getService().getName(), q.getService().getDepartment().getName(), branch.getName(),
                branch.getOrganization().getName(),
                waiting, serving, lastCalled,
                Math.round(estimate.avgServiceMinutes() * 10) / 10.0,
                estimate.minutesFor(waiting), estimate.confidence(),
                blocker == null, blocker == null ? null : blocker.name(),
                cfg.getPriorityMode() != PriorityMode.DISABLED,
                Instant.now());
    }

    // ------------------------------------------------------------------ customer token view

    public TokenView tokenView(QueueToken t, ServiceQueue q, QueueConfiguration cfg) {
        UUID queueId = q.getId();
        long ahead = t.getStatus() == TokenStatus.WAITING ? peopleAhead(t, q, cfg) : 0;
        var estimate = estimation.estimate(queueId, cfg);

        List<QueueToken> active = tokens.findByQueueIdAndStatusInOrderByCalledAtDesc(queueId, IN_PROGRESS);
        String currentlyServing = !active.isEmpty() ? active.get(0).getTokenNumber()
                : tokens.findFirstByQueueIdAndServiceDateAndCalledAtIsNotNullOrderByCalledAtDesc(
                        queueId, QueueTime.today(q)).map(QueueToken::getTokenNumber).orElse(null);

        boolean waiting = t.getStatus() == TokenStatus.WAITING;
        boolean near = waiting && ahead <= cfg.getNearTurnThreshold();
        String counterName = t.getCounter() == null ? null : t.getCounter().getName();
        Branch branch = q.getService().getDepartment().getBranch();

        return new TokenView(
                t.getReference(), t.getTokenNumber(), t.getStatus(), t.isPriority(),
                queueId, q.getPublicRef(), q.getName(), q.getStatus(),
                q.getService().getName(), branch.getName(), branch.getOrganization().getName(),
                currentlyServing,
                (int) ahead, waiting ? (int) ahead + 1 : 0,
                waiting ? estimate.minutesFor(ahead) : null,
                waiting ? estimate.confidence() : null,
                counterName, near, message(t.getStatus(), counterName, near, ahead),
                t.getCreatedAt(), t.getCalledAt(), t.getUpdatedAt(), Instant.now());
    }

    private static String message(TokenStatus status, String counterName, boolean near, long ahead) {
        return switch (status) {
            case WAITING -> ahead == 0 ? "You're next. Please stay close."
                    : near ? "Your turn is coming up." : "You're in line. We'll update this page automatically.";
            case CALLED -> counterName != null ? "PLEASE PROCEED TO " + counterName.toUpperCase()
                    : "PLEASE PROCEED TO THE COUNTER";
            case SERVING -> counterName != null ? "You are being served at " + counterName + "."
                    : "You are being served.";
            case COMPLETED -> "Thank you! Your visit is complete.";
            case CANCELLED -> "This token was cancelled.";
            case SKIPPED -> "You were skipped. Please speak to the desk to rejoin the line.";
            case NO_SHOW -> "You did not arrive when called and were marked as a no-show.";
        };
    }

    /** Number of waiting tokens that will be served before this one. */
    public long peopleAhead(QueueToken t, ServiceQueue q, QueueConfiguration cfg) {
        UUID queueId = q.getId();
        long order = t.getQueueOrder();
        PriorityMode mode = cfg.getPriorityMode();
        long priorityWaiting = 0;
        long normalWaiting = 0;
        long priorityBefore = 0;
        long normalBefore = 0;
        long anyBefore = 0;
        switch (mode) {
            case DISABLED -> anyBefore = tokens.countByQueueIdAndStatusAndQueueOrderLessThan(
                    queueId, TokenStatus.WAITING, order);
            case ALWAYS_FIRST -> {
                if (t.isPriority()) {
                    priorityBefore = tokens.countByQueueIdAndStatusAndPriorityAndQueueOrderLessThan(
                            queueId, TokenStatus.WAITING, true, order);
                } else {
                    priorityWaiting = tokens.countByQueueIdAndStatusAndPriority(queueId, TokenStatus.WAITING, true);
                    normalBefore = tokens.countByQueueIdAndStatusAndPriorityAndQueueOrderLessThan(
                            queueId, TokenStatus.WAITING, false, order);
                }
            }
            case AFTER_N -> {
                priorityWaiting = tokens.countByQueueIdAndStatusAndPriority(queueId, TokenStatus.WAITING, true);
                normalWaiting = tokens.countByQueueIdAndStatusAndPriority(queueId, TokenStatus.WAITING, false);
                priorityBefore = tokens.countByQueueIdAndStatusAndPriorityAndQueueOrderLessThan(
                        queueId, TokenStatus.WAITING, true, order);
                normalBefore = tokens.countByQueueIdAndStatusAndPriorityAndQueueOrderLessThan(
                        queueId, TokenStatus.WAITING, false, order);
            }
        }
        return QueueOrdering.peopleAhead(mode, cfg.getPriorityAfterN(), q.getPriorityStreak(), t.isPriority(),
                priorityWaiting, normalWaiting, priorityBefore, normalBefore, anyBefore);
    }

    // ------------------------------------------------------------------ staff view

    public StaffTokenDto staffToken(QueueToken t) {
        Instant end = t.getCalledAt() != null ? t.getCalledAt()
                : t.getCancelledAt() != null ? t.getCancelledAt() : Instant.now();
        long waitedSeconds = Math.max(0, Duration.between(t.getCreatedAt(), end).toSeconds());
        Counter c = t.getCounter();
        return new StaffTokenDto(t.getId(), t.getTokenNumber(), t.getStatus(), t.isPriority(),
                t.getCustomerName(), c == null ? null : c.getId(), c == null ? null : c.getName(),
                t.getCreatedAt(), t.getCalledAt(), t.getServingAt(), t.getCompletedAt(), waitedSeconds);
    }

    public QueueStatsDto stats(ServiceQueue q, QueueConfiguration cfg) {
        UUID id = q.getId();
        LocalDate today = QueueTime.today(q);
        Double avgWait = tokens.averageWaitSeconds(id, today);
        return new QueueStatsDto(
                tokens.countByQueueIdAndStatus(id, TokenStatus.WAITING),
                tokens.countByQueueIdAndStatus(id, TokenStatus.CALLED),
                tokens.countByQueueIdAndStatus(id, TokenStatus.SERVING),
                tokens.countByQueueIdAndStatusAndServiceDate(id, TokenStatus.COMPLETED, today),
                tokens.countByQueueIdAndStatusAndServiceDate(id, TokenStatus.CANCELLED, today),
                tokens.countByQueueIdAndStatusAndServiceDate(id, TokenStatus.SKIPPED, today),
                tokens.countByQueueIdAndStatusAndServiceDate(id, TokenStatus.NO_SHOW, today),
                tokens.countByQueueIdAndServiceDate(id, today),
                avgWait == null ? 0 : Math.round(avgWait / 6.0) / 10.0,
                Math.round(estimation.estimate(id, cfg).avgServiceMinutes() * 10) / 10.0);
    }

    public StaffQueueState staffState(ServiceQueue q, QueueConfiguration cfg) {
        UUID id = q.getId();
        List<QueueToken> active = tokens.findByQueueIdAndStatusInOrderByCalledAtDesc(id, IN_PROGRESS);
        List<QueueToken> waitingByOrder = tokens.findByQueueIdAndStatusInOrderByQueueOrderAsc(
                id, EnumSet.of(TokenStatus.WAITING));
        List<QueueToken> waiting = QueueOrdering.servingOrder(waitingByOrder, cfg.getPriorityMode(),
                cfg.getPriorityAfterN(), q.getPriorityStreak());
        List<QueueToken> recent = tokens.findByQueueIdAndStatusInAndServiceDateOrderByUpdatedAtDesc(
                id, FINISHED, QueueTime.today(q), PageRequest.of(0, RECENT_LIMIT));

        Map<UUID, String> busyCounters = active.stream()
                .filter(t -> t.getCounter() != null)
                .collect(Collectors.toMap(t -> t.getCounter().getId(), QueueToken::getTokenNumber, (a, b) -> a));
        List<CounterDto> counterDtos = counters.findByQueueIdOrderByNameAsc(id).stream()
                .map(c -> new CounterDto(c.getId(), c.getName(), c.isActive(), busyCounters.get(c.getId())))
                .toList();

        List<StaffTokenDto> waitingDtos = waiting.stream().map(this::staffToken).toList();
        return new StaffQueueState(
                snapshot(q, cfg), stats(q, cfg),
                active.stream().map(this::staffToken).toList(),
                waitingDtos,
                recent.stream().map(this::staffToken).toList(),
                waitingDtos.isEmpty() ? null : waitingDtos.get(0),
                counterDtos);
    }

    // ------------------------------------------------------------------ helpers

    private ServiceQueue tokensQueue(UUID queueId) {
        return queues.findWithDetailsById(queueId)
                .orElseThrow(() -> new ApiException(ErrorCode.QUEUE_NOT_FOUND));
    }

    private QueueConfiguration requireConfig(UUID queueId) {
        return configs.findByQueueId(queueId).orElseThrow(() -> new ApiException(ErrorCode.QUEUE_NOT_FOUND));
    }
}
