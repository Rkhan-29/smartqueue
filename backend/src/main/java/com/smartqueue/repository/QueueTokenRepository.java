package com.smartqueue.repository;

import com.smartqueue.entity.QueueToken;
import com.smartqueue.entity.TokenStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueTokenRepository extends JpaRepository<QueueToken, UUID>, JpaSpecificationExecutor<QueueToken> {

    String DETAIL_NOTE = "Detail graphs load queue, service, department, branch, organization and counter in one query";

    @EntityGraph(attributePaths = {"queue", "queue.service", "queue.service.department",
            "queue.service.department.branch", "counter"})
    Optional<QueueToken> findWithDetailsByReference(String reference);

    @EntityGraph(attributePaths = {"queue", "queue.service", "queue.service.department",
            "queue.service.department.branch", "counter"})
    Optional<QueueToken> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"queue", "queue.service", "queue.service.department",
            "queue.service.department.branch", "counter"})
    List<QueueToken> findWithDetailsBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);

    @EntityGraph(attributePaths = {"queue", "queue.service", "queue.service.department",
            "queue.service.department.branch", "counter"})
    List<QueueToken> findWithDetailsByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "queue")
    org.springframework.data.domain.Page<QueueToken> findAll(
            org.springframework.data.jpa.domain.Specification<QueueToken> spec, Pageable pageable);

    // ---- Queue ordering (all callers hold the queue row lock) ----

    /** Lightweight lookups used to find which queue to lock before touching a token. */
    @Query("select t.queue.id from QueueToken t where t.id = :id")
    Optional<UUID> findQueueIdById(@Param("id") UUID id);

    @Query("select t.queue.id from QueueToken t where t.reference = :reference")
    Optional<UUID> findQueueIdByReference(@Param("reference") String reference);

    @EntityGraph(attributePaths = "counter")
    Optional<QueueToken> findFirstByQueueIdAndServiceDateAndCalledAtIsNotNullOrderByCalledAtDesc(UUID queueId,
                                                                                                 LocalDate date);

    @EntityGraph(attributePaths = "counter")
    Optional<QueueToken> findFirstByQueueIdAndStatusOrderByQueueOrderAsc(UUID queueId, TokenStatus status);

    @EntityGraph(attributePaths = "counter")
    Optional<QueueToken> findFirstByQueueIdAndStatusAndPriorityOrderByQueueOrderAsc(UUID queueId,
                                                                                    TokenStatus status,
                                                                                    boolean priority);

    // ---- Counting ----

    long countByQueueIdAndStatus(UUID queueId, TokenStatus status);

    long countByQueueIdAndStatusAndPriority(UUID queueId, TokenStatus status, boolean priority);

    long countByQueueIdAndStatusAndQueueOrderLessThan(UUID queueId, TokenStatus status, long queueOrder);

    long countByQueueIdAndStatusAndPriorityAndQueueOrderLessThan(UUID queueId, TokenStatus status,
                                                                 boolean priority, long queueOrder);

    long countByQueueIdAndStatusInAndServiceDate(UUID queueId, Collection<TokenStatus> statuses, LocalDate date);

    long countByQueueIdAndServiceDate(UUID queueId, LocalDate date);

    long countByQueueIdAndStatusAndServiceDate(UUID queueId, TokenStatus status, LocalDate date);

    boolean existsByCounterIdAndStatusIn(UUID counterId, Collection<TokenStatus> statuses);

    boolean existsByQueueIdAndSessionIdAndStatusIn(UUID queueId, String sessionId, Collection<TokenStatus> statuses);

    // ---- Staff views ----

    @EntityGraph(attributePaths = "counter")
    List<QueueToken> findByQueueIdAndStatusInOrderByQueueOrderAsc(UUID queueId, Collection<TokenStatus> statuses);

    @EntityGraph(attributePaths = "counter")
    List<QueueToken> findByQueueIdAndStatusInAndServiceDateOrderByUpdatedAtDesc(UUID queueId,
                                                                                Collection<TokenStatus> statuses,
                                                                                LocalDate date, Pageable pageable);

    /** Token currently being called/served, most recently called first. */
    @EntityGraph(attributePaths = "counter")
    List<QueueToken> findByQueueIdAndStatusInOrderByCalledAtDesc(UUID queueId, Collection<TokenStatus> statuses);

    // ---- Estimation inputs ----

    @Query(value = """
            select avg(extract(epoch from (completed_at - coalesce(serving_at, called_at))))
            from queue_tokens
            where queue_id = :queueId and status = 'COMPLETED'
              and completed_at >= :since
              and coalesce(serving_at, called_at) is not null
            """, nativeQuery = true)
    Double averageServiceSeconds(@Param("queueId") UUID queueId, @Param("since") Instant since);

    @Query(value = """
            select count(*) from queue_tokens
            where queue_id = :queueId and status = 'COMPLETED'
              and completed_at >= :since
              and coalesce(serving_at, called_at) is not null
            """, nativeQuery = true)
    long completedSampleCount(@Param("queueId") UUID queueId, @Param("since") Instant since);

    @Query(value = """
            select avg(extract(epoch from (coalesce(called_at, serving_at) - created_at)))
            from queue_tokens
            where queue_id = :queueId and service_date = :date
              and coalesce(called_at, serving_at) is not null
            """, nativeQuery = true)
    Double averageWaitSeconds(@Param("queueId") UUID queueId, @Param("date") LocalDate date);

    // ---- Maintenance (no-show / auto-expire) ----

    @EntityGraph(attributePaths = "queue")
    List<QueueToken> findByStatus(TokenStatus status);

    @Query("select t.id from QueueToken t where t.queue.id = :queueId and t.status = :status and t.createdAt < :cutoff")
    List<UUID> findIdsOlderThan(@Param("queueId") UUID queueId, @Param("status") TokenStatus status,
                                @Param("cutoff") Instant cutoff);
}
