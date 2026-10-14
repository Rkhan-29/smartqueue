package com.smartqueue.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "queue_configurations")
@Getter
@Setter
public class QueueConfiguration extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "queue_id", nullable = false, unique = true)
    private ServiceQueue queue;

    @Column(name = "token_prefix", nullable = false, length = 6)
    private String tokenPrefix = "A";

    @Column(name = "priority_prefix", nullable = false, length = 6)
    private String priorityPrefix = "P";

    @Column(name = "starting_number", nullable = false)
    private int startingNumber = 1;

    @Column(name = "number_padding", nullable = false)
    private int numberPadding = 3;

    @Column(name = "max_queue_size", nullable = false)
    private int maxQueueSize = 200;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority_mode", nullable = false, length = 16)
    private PriorityMode priorityMode = PriorityMode.DISABLED;

    @Column(name = "priority_after_n", nullable = false)
    private int priorityAfterN = 3;

    @Column(name = "avg_service_minutes", nullable = false)
    private int avgServiceMinutes = 5;

    @Column(name = "opening_time")
    private LocalTime openingTime;

    @Column(name = "closing_time")
    private LocalTime closingTime;

    @Column(name = "break_start")
    private LocalTime breakStart;

    @Column(name = "break_end")
    private LocalTime breakEnd;

    @Column(name = "auto_expire_minutes", nullable = false)
    private int autoExpireMinutes = 240;

    @Column(name = "no_show_timeout_minutes", nullable = false)
    private int noShowTimeoutMinutes = 5;

    @Column(name = "allow_duplicate_active", nullable = false)
    private boolean allowDuplicateActive = false;

    /** If true, NEXT moves a token straight to SERVING instead of CALLED. */
    @Column(name = "direct_serving", nullable = false)
    private boolean directServing = false;

    /** "Your turn is coming up" is shown when people-ahead is at or below this value. */
    @Column(name = "near_turn_threshold", nullable = false)
    private int nearTurnThreshold = 3;
}
