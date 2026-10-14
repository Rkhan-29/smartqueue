package com.smartqueue.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "queue_tokens")
@Getter
@Setter
public class QueueToken extends BaseEntity {

    /** Unguessable public reference; the guest's capability to view/cancel their own token. */
    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "queue_id", nullable = false)
    private ServiceQueue queue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counter_id")
    private Counter counter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "token_number", nullable = false, length = 20)
    private String tokenNumber;

    @Column(nullable = false)
    private boolean priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TokenStatus status = TokenStatus.WAITING;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    /** Ordering key within the waiting line; allows "move token" without renumbering. */
    @Column(name = "queue_order", nullable = false)
    private long queueOrder;

    @Column(name = "called_at")
    private Instant calledAt;

    @Column(name = "serving_at")
    private Instant servingAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    private long version;
}
