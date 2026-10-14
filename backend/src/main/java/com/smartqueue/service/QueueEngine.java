package com.smartqueue.service;

import com.smartqueue.dto.QueueDtos.JoinRequest;
import com.smartqueue.dto.QueueDtos.JoinResponse;
import com.smartqueue.dto.QueueDtos.QueueEvent;
import com.smartqueue.dto.QueueDtos.QueueSnapshot;
import com.smartqueue.dto.QueueDtos.StaffTokenDto;
import com.smartqueue.dto.QueueDtos.TokenEvent;
import com.smartqueue.dto.QueueDtos.TokenView;
import com.smartqueue.entity.Counter;
import com.smartqueue.entity.QueueConfiguration;
import com.smartqueue.entity.QueueDailyCounter;
import com.smartqueue.entity.QueueStatus;
import com.smartqueue.entity.QueueToken;
import com.smartqueue.entity.Role;
import com.smartqueue.entity.ServiceQueue;
import com.smartqueue.entity.TokenStatus;
import com.smartqueue.entity.PriorityMode;
import com.smartqueue.exception.ApiException;
import com.smartqueue.exception.ErrorCode;
import com.smartqueue.repository.AppUserRepository;
import com.smartqueue.repository.CounterRepository;
import com.smartqueue.repository.QueueConfigurationRepository;
import com.smartqueue.repository.QueueDailyCounterRepository;
import com.smartqueue.repository.QueueTokenRepository;
import com.smartqueue.repository.ServiceQueueRepository;
import com.smartqueue.security.AuthenticatedUser;
import com.smartqueue.security.CurrentUser;
import com.smartqueue.security.TokenHasher;
import com.smartqueue.service.notification.NotificationService;
import com.smartqueue.websocket.RealtimePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The authoritative queue/token engine.
 *
 * Concurrency model: every mutating operation first takes a PESSIMISTIC_WRITE lock on the queue row
 * (SELECT ... FOR UPDATE) and only then reads token state. Operations on one queue are therefore
 * strictly serialised by the database, which guarantees unique, gap-free token numbers, that a token
 * can never be called twice, and consistent queue positions. Different queues never block each other.
 * The @Version column on tokens is a second line of defence.
 */
@Service
@RequiredArgsConstructor
public class QueueEngine {

    private static final Set<TokenStatus> IN_PROGRESS = EnumSet.of(TokenStatus.CALLED, TokenStatus.SERVING);
    private static final Pattern SESSION_ID = Pattern.compile("^[A-Za-z0-9_-]{22,64}$");

    private final ServiceQueueRepository queues;
    private final QueueConfigurationRepository configs;
    private final QueueTokenRepository tokens;
    private final QueueDailyCounterRepository dailyCounters;
    private final CounterRepository counters;
    private final AppUserRepository users;
    private final QueueViewService views;
    private final RealtimePublisher realtime;
    private final NotificationService notifications;
    private final AuditService audit;
    private final QueueAccessService access;

    private record Ctx(ServiceQueue queue, QueueConfiguration cfg) {
    }

    // =====================================================================================
    // Customer operations
    // =====================================================================================

    /**
     * Issues a token. Guests are identified by an opaque session id; logged-in customers by their account;
     * staff issue walk-in tokens (optionally priority) on behalf of people at the desk.
     */
    @Transactional
    public JoinResponse join(UUID queueId, JoinRequest request, String clientSessionId) {
        AuthenticatedUser caller = CurrentUser.get().orElse(null);
        boolean staffCaller = caller != null && (caller.role() == Role.STAFF || caller.role() == Role.ADMIN);
        if (staffCaller) {
            access.requireStaffAccess(queueId);
        }
        boolean priority = request != null && request.priorityRequested();
        if (priority && !staffCaller) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only staff can issue priority tokens.");
        }

        Ctx ctx = lock(queueId);
        ServiceQueue q = ctx.queue();
        QueueConfiguration cfg = ctx.cfg();
        if (!q.isActive()) {
            throw new ApiException(ErrorCode.QUEUE_NOT_FOUND);
        }
        if (priority && cfg.getPriorityMode() == PriorityMode.DISABLED) {
            throw new ApiException(ErrorCode.PRIORITY_DISABLED);
        }
        long waiting = tokens.countByQueueIdAndStatus(queueId, TokenStatus.WAITING);
        ErrorCode blocker = views.joinBlocker(q, cfg, waiting);
        if (blocker != null) {
            throw new ApiException(blocker);
        }

        String sessionId;
        String guestSession = null;
        QueueToken token = new QueueToken();
        if (staffCaller) {
            sessionId = "walkin-" + TokenHasher.randomUrlSafe(12);
        } else if (caller != null) {
            sessionId = "u:" + caller.id();
            token.setUser(users.getReferenceById(caller.id()));
        } else {
            sessionId = clientSessionId != null && SESSION_ID.matcher(clientSessionId).matches()
                    ? clientSessionId : TokenHasher.randomUrlSafe(24);
            guestSession = sessionId;
        }

        if (!cfg.isAllowDuplicateActive()
                && tokens.existsByQueueIdAndSessionIdAndStatusIn(queueId, sessionId, TokenStatus.activeStatuses())) {
            throw new ApiException(ErrorCode.DUPLICATE_ACTIVE_TOKEN);
        }

        LocalDate today = QueueTime.today(q);
        QueueDailyCounter counter = dailyCounters
                .findByQueueIdAndServiceDateAndPriority(queueId, today, priority)
                .orElseGet(() -> {
                    QueueDailyCounter c = new QueueDailyCounter();
                    c.setQueueId(queueId);
                    c.setServiceDate(today);
                    c.setPriority(priority);
                    c.setLastNumber(cfg.getStartingNumber() - 1);
                    return c;
                });
        counter.setLastNumber(counter.getLastNumber() + 1);
        dailyCounters.save(counter);

        String prefix = priority ? cfg.getPriorityPrefix() : cfg.getTokenPrefix();
        q.setLastOrder(q.getLastOrder() + 1);

        token.setReference(TokenHasher.randomUrlSafe(18));
        token.setQueue(q);
        token.setSessionId(sessionId);
        token.setServiceDate(today);
        token.setSequenceNumber(counter.getLastNumber());
        token.setTokenNumber(prefix + String.format("%0" + cfg.getNumberPadding() + "d", counter.getLastNumber()));
        token.setPriority(priority);
        token.setStatus(TokenStatus.WAITING);
        token.setQueueOrder(q.getLastOrder());
        if (request != null && request.customerName() != null && !request.customerName().isBlank()) {
            token.setCustomerName(request.customerName().trim());
        }
        tokens.save(token);

        audit.record("TOKEN_GENERATED", "TOKEN", token.getId(),
                Map.of("token", token.getTokenNumber(), "queue", q.getName(), "priority", priority));
        publish(ctx, "JOINED", token, "You're in line");
        return new JoinResponse(views.tokenView(token, q, cfg), guestSession);
    }

    /** Customer leaves the line. Possession of the unguessable reference is the authorisation. */
    @Transactional
    public TokenView customerCancel(String reference) {
        UUID queueId = tokens.findQueueIdByReference(reference)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_NOT_FOUND));
        Ctx ctx = lock(queueId);
        QueueToken token = tokens.findWithDetailsByReference(reference)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_NOT_FOUND));
        if (token.getStatus() != TokenStatus.WAITING && token.getStatus() != TokenStatus.CALLED) {
            throw invalidState(token, TokenStatus.CANCELLED);
        }
        transition(token, TokenStatus.CANCELLED);
        audit.record("TOKEN_CANCELLED", "TOKEN", token.getId(),
                Map.of("token", token.getTokenNumber(), "by", "customer"));
        publish(ctx, "CANCELLED", token, "Token cancelled");
        return views.tokenView(token, ctx.queue(), ctx.cfg());
    }

    @Transactional(readOnly = true)
    public TokenView tokenByReference(String reference) {
        QueueToken token = tokens.findWithDetailsByReference(reference)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_NOT_FOUND));
        ServiceQueue q = token.getQueue();
        QueueConfiguration cfg = configs.findByQueueId(q.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.QUEUE_NOT_FOUND));
        return views.tokenView(token, q, cfg);
    }

    /** Token history for the signed-in customer, newest first. */
    @Transactional(readOnly = true)
    public List<TokenView> myTokens(int limit) {
        AuthenticatedUser user = CurrentUser.require();
        return tokens.findWithDetailsByUserIdOrderByCreatedAtDesc(user.id(),
                        org.springframework.data.domain.PageRequest.of(0, Math.min(Math.max(limit, 1), 50)))
                .stream()
                .map(t -> views.tokenView(t, t.getQueue(), configs.findByQueueId(t.getQueue().getId())
                        .orElseThrow(() -> new ApiException(ErrorCode.QUEUE_NOT_FOUND))))
                .toList();
    }

    // =====================================================================================
    // Staff operations
    // =====================================================================================

    /** NEXT: atomically picks the next valid waiting token and calls it. */
    @Transactional
    public StaffTokenDto next(UUID queueId, UUID counterId) {
        access.requireStaffAccess(queueId);
        Ctx ctx = lock(queueId);
        ServiceQueue q = ctx.queue();
        QueueConfiguration cfg = ctx.cfg();
        requireOperational(q);

        Counter counter = resolveFreeCounter(queueId, counterId);

        Optional<QueueToken> firstPriority = tokens
                .findFirstByQueueIdAndStatusAndPriorityOrderByQueueOrderAsc(queueId, TokenStatus.WAITING, true);
        Optional<QueueToken> firstNormal = tokens
                .findFirstByQueueIdAndStatusAndPriorityOrderByQueueOrderAsc(queueId, TokenStatus.WAITING, false);
        if (firstPriority.isEmpty() && firstNormal.isEmpty()) {
            throw new ApiException(ErrorCode.NO_WAITING_TOKEN);
        }
        QueueOrdering.Pick pick = QueueOrdering.choose(cfg.getPriorityMode(), cfg.getPriorityAfterN(),
                q.getPriorityStreak(),
                firstPriority.map(QueueToken::getQueueOrder).orElse(null),
                firstNormal.map(QueueToken::getQueueOrder).orElse(null));
        QueueToken chosen = pick == QueueOrdering.Pick.PRIORITY ? firstPriority.orElseThrow()
                : firstNormal.orElseThrow();
        q.setPriorityStreak(QueueOrdering.nextStreak(pick, cfg.getPriorityAfterN(), q.getPriorityStreak()));

        transition(chosen, cfg.isDirectServing() ? TokenStatus.SERVING : TokenStatus.CALLED);
        chosen.setCounter(counter);
        tokens.save(chosen);

        audit.record("TOKEN_CALLED", "TOKEN", chosen.getId(), tokenMeta(chosen, q, counter));
        publish(ctx, "CALLED", chosen, "It's your turn");
        return views.staffToken(chosen);
    }

    /** Re-announces a called token (resets the no-show timer), optionally at another counter. */
    @Transactional
    public StaffTokenDto recall(UUID tokenId, UUID counterId) {
        Ctx ctx = lockForToken(tokenId);
        QueueToken token = loadToken(tokenId);
        if (token.getStatus() != TokenStatus.CALLED) {
            throw invalidState(token, TokenStatus.CALLED);
        }
        if (counterId != null && (token.getCounter() == null || !counterId.equals(token.getCounter().getId()))) {
            token.setCounter(resolveFreeCounter(ctx.queue().getId(), counterId));
        }
        token.setCalledAt(Instant.now());
        tokens.save(token);
        audit.record("TOKEN_RECALLED", "TOKEN", token.getId(), tokenMeta(token, ctx.queue(), token.getCounter()));
        publish(ctx, "RECALLED", token, "You're being called again");
        return views.staffToken(token);
    }

    @Transactional
    public StaffTokenDto startServing(UUID tokenId) {
        return applyStaffTransition(tokenId, TokenStatus.SERVING, "TOKEN_SERVING", "SERVING", "Service started");
    }

    @Transactional
    public StaffTokenDto complete(UUID tokenId) {
        return applyStaffTransition(tokenId, TokenStatus.COMPLETED, "TOKEN_COMPLETED", "COMPLETED", "Visit complete");
    }

    @Transactional
    public StaffTokenDto skip(UUID tokenId) {
        return applyStaffTransition(tokenId, TokenStatus.SKIPPED, "TOKEN_SKIPPED", "SKIPPED", "You were skipped");
    }

    @Transactional
    public StaffTokenDto markNoShow(UUID tokenId) {
        return applyStaffTransition(tokenId, TokenStatus.NO_SHOW, "TOKEN_NO_SHOW", "NO_SHOW", "Marked as no-show");
    }

    @Transactional
    public StaffTokenDto cancel(UUID tokenId) {
        return applyStaffTransition(tokenId, TokenStatus.CANCELLED, "TOKEN_CANCELLED", "CANCELLED",
                "Token cancelled");
    }

    /** Puts a skipped (or called-but-absent) token back at the end of the line. */
    @Transactional
    public StaffTokenDto requeue(UUID tokenId) {
        Ctx ctx = lockForToken(tokenId);
        QueueToken token = loadToken(tokenId);
        transition(token, TokenStatus.WAITING);
        ServiceQueue q = ctx.queue();
        q.setLastOrder(q.getLastOrder() + 1);
        token.setQueueOrder(q.getLastOrder());
        tokens.save(token);
        audit.record("TOKEN_REQUEUED", "TOKEN", token.getId(), tokenMeta(token, q, null));
        publish(ctx, "REQUEUED", token, "Back in line");
        return views.staffToken(token);
    }

    /** Moves a waiting token to a 1-based position within its own (priority or normal) line. */
    @Transactional
    public StaffTokenDto move(UUID tokenId, int position) {
        Ctx ctx = lockForToken(tokenId);
        QueueToken token = loadToken(tokenId);
        if (token.getStatus() != TokenStatus.WAITING) {
            throw invalidState(token, TokenStatus.WAITING);
        }
        List<QueueToken> line = tokens
                .findByQueueIdAndStatusInOrderByQueueOrderAsc(ctx.queue().getId(), Set.of(TokenStatus.WAITING))
                .stream()
                .filter(t -> t.isPriority() == token.isPriority())
                .collect(Collectors.toCollection(ArrayList::new));
        // Re-use the same set of order keys, permuted: keeps keys unique and the queue's counter valid.
        List<Long> orderKeys = line.stream().map(QueueToken::getQueueOrder).toList();
        line.remove(token);
        line.add(Math.min(Math.max(position, 1) - 1, line.size()), token);
        for (int i = 0; i < line.size(); i++) {
            line.get(i).setQueueOrder(orderKeys.get(i));
        }
        tokens.saveAll(line);
        audit.record("TOKEN_MOVED", "TOKEN", token.getId(),
                Map.of("token", token.getTokenNumber(), "position", position));
        publish(ctx, "MOVED", token, null);
        return views.staffToken(token);
    }

    @Transactional
    public QueueSnapshot pause(UUID queueId) {
        return changeQueueStatus(queueId, QueueStatus.PAUSED, "QUEUE_PAUSED");
    }

    @Transactional
    public QueueSnapshot resume(UUID queueId) {
        return changeQueueStatus(queueId, QueueStatus.ACTIVE, "QUEUE_RESUMED");
    }

    private QueueSnapshot changeQueueStatus(UUID queueId, QueueStatus target, String auditAction) {
        access.requireStaffAccess(queueId);
        Ctx ctx = lock(queueId);
        ServiceQueue q = ctx.queue();
        if (q.getStatus() == QueueStatus.CLOSED) {
            throw new ApiException(ErrorCode.QUEUE_CLOSED);
        }
        q.setStatus(target);
        audit.record(auditAction, "QUEUE", q.getId(), Map.of("queue", q.getName()));
        publish(ctx, auditAction, null, null);
        return views.snapshot(q, ctx.cfg());
    }

    // =====================================================================================
    // System (scheduler) operations
    // =====================================================================================

    /** Called by the maintenance job. Re-validates under the lock before acting. */
    @Transactional
    public boolean autoMarkNoShow(UUID tokenId) {
        UUID queueId = tokens.findQueueIdById(tokenId).orElse(null);
        if (queueId == null) {
            return false;
        }
        Ctx ctx = lock(queueId);
        QueueToken token = loadToken(tokenId);
        Duration timeout = Duration.ofMinutes(ctx.cfg().getNoShowTimeoutMinutes());
        if (token.getStatus() != TokenStatus.CALLED || token.getCalledAt() == null
                || token.getCalledAt().plus(timeout).isAfter(Instant.now())) {
            return false;
        }
        transition(token, TokenStatus.NO_SHOW);
        audit.recordAnonymous("system", "TOKEN_NO_SHOW", "TOKEN", token.getId(),
                Map.of("token", token.getTokenNumber(), "reason", "timeout"));
        publish(ctx, "NO_SHOW", token, "Marked as no-show");
        return true;
    }

    @Transactional
    public boolean autoExpire(UUID tokenId) {
        UUID queueId = tokens.findQueueIdById(tokenId).orElse(null);
        if (queueId == null) {
            return false;
        }
        Ctx ctx = lock(queueId);
        QueueToken token = loadToken(tokenId);
        Duration ttl = Duration.ofMinutes(ctx.cfg().getAutoExpireMinutes());
        if (token.getStatus() != TokenStatus.WAITING || token.getCreatedAt().plus(ttl).isAfter(Instant.now())) {
            return false;
        }
        transition(token, TokenStatus.CANCELLED);
        audit.recordAnonymous("system", "TOKEN_EXPIRED", "TOKEN", token.getId(),
                Map.of("token", token.getTokenNumber(), "reason", "auto_expire"));
        publish(ctx, "EXPIRED", token, "Token expired");
        return true;
    }

    // =====================================================================================
    // Internals
    // =====================================================================================

    private StaffTokenDto applyStaffTransition(UUID tokenId, TokenStatus target, String auditAction,
                                               String eventType, String title) {
        Ctx ctx = lockForToken(tokenId);
        QueueToken token = loadToken(tokenId);
        transition(token, target);
        tokens.save(token);
        audit.record(auditAction, "TOKEN", token.getId(), tokenMeta(token, ctx.queue(), token.getCounter()));
        publish(ctx, eventType, token, title);
        return views.staffToken(token);
    }

    /** Locks the queue row. Everything that reads or changes token state must happen after this. */
    private Ctx lock(UUID queueId) {
        ServiceQueue q = queues.lockById(queueId)
                .orElseThrow(() -> new ApiException(ErrorCode.QUEUE_NOT_FOUND));
        QueueConfiguration cfg = configs.findByQueueId(queueId)
                .orElseThrow(() -> new ApiException(ErrorCode.QUEUE_NOT_FOUND));
        return new Ctx(q, cfg);
    }

    /** Resolves a token's queue, checks the caller may operate it, then locks it. */
    private Ctx lockForToken(UUID tokenId) {
        UUID queueId = tokens.findQueueIdById(tokenId)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_NOT_FOUND));
        access.requireStaffAccess(queueId);
        return lock(queueId);
    }

    /** Loads the token fresh, after the queue lock is held, so its status cannot be stale. */
    private QueueToken loadToken(UUID tokenId) {
        return tokens.findWithDetailsById(tokenId)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_NOT_FOUND));
    }

    private void requireOperational(ServiceQueue q) {
        if (q.getStatus() == QueueStatus.PAUSED) {
            throw new ApiException(ErrorCode.QUEUE_PAUSED);
        }
        if (q.getStatus() == QueueStatus.CLOSED) {
            throw new ApiException(ErrorCode.QUEUE_CLOSED);
        }
    }

    private Counter resolveFreeCounter(UUID queueId, UUID counterId) {
        if (counterId == null) {
            return null;
        }
        Counter counter = counters.findByIdAndQueueId(counterId, queueId)
                .filter(Counter::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Counter not found."));
        if (tokens.existsByCounterIdAndStatusIn(counterId, IN_PROGRESS)) {
            throw new ApiException(ErrorCode.COUNTER_BUSY);
        }
        return counter;
    }

    /** The single place where token status changes. Rejects anything the state machine forbids. */
    private void transition(QueueToken token, TokenStatus next) {
        if (!token.getStatus().canTransitionTo(next)) {
            throw invalidState(token, next);
        }
        Instant now = Instant.now();
        switch (next) {
            case CALLED -> token.setCalledAt(now);
            case SERVING -> {
                if (token.getCalledAt() == null) {
                    token.setCalledAt(now);
                }
                token.setServingAt(now);
            }
            case COMPLETED -> token.setCompletedAt(now);
            case CANCELLED -> token.setCancelledAt(now);
            case WAITING -> {
                token.setCalledAt(null);
                token.setServingAt(null);
                token.setCounter(null);
            }
            case SKIPPED, NO_SHOW -> {
                // timestamps for these are derivable from updatedAt
            }
        }
        token.setStatus(next);
    }

    private ApiException invalidState(QueueToken token, TokenStatus attempted) {
        return new ApiException(ErrorCode.INVALID_TOKEN_STATE,
                "Token " + token.getTokenNumber() + " is " + token.getStatus()
                        + " and cannot be changed to " + attempted + ".");
    }

    private Map<String, Object> tokenMeta(QueueToken token, ServiceQueue q, Counter counter) {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("token", token.getTokenNumber());
        meta.put("queue", q.getName());
        if (counter != null) {
            meta.put("counter", counter.getName());
        }
        return meta;
    }

    /** Builds the realtime payloads inside the transaction; they are delivered only after commit. */
    private void publish(Ctx ctx, String type, QueueToken subject, String notificationTitle) {
        ServiceQueue q = ctx.queue();
        QueueSnapshot snapshot = views.snapshot(q, ctx.cfg());
        realtime.queueEvent(q.getPublicRef(),
                new QueueEvent(type, snapshot, subject == null ? null : subject.getTokenNumber(), Instant.now()));
        if (subject != null) {
            TokenView view = views.tokenView(subject, q, ctx.cfg());
            realtime.tokenEvent(subject.getReference(),
                    new TokenEvent(type, view, notificationTitle, view.message(), Instant.now()));
            if (notificationTitle != null) {
                notifications.dispatch(subject, type, notificationTitle, view.message());
            }
        }
    }
}
