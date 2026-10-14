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

@Entity
@Table(name = "queues")
@Getter
@Setter
public class ServiceQueue extends BaseEntity {

    /** Opaque public identifier used in QR codes and public URLs. */
    @Column(name = "public_ref", nullable = false, unique = true, length = 32)
    private String publicRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceOffering service;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QueueStatus status = QueueStatus.ACTIVE;

    /** Number of consecutive normal tokens served since the last priority token (AFTER_N mode). */
    @Column(name = "priority_streak", nullable = false)
    private int priorityStreak;

    /** Monotonic ordering key handed to the next joining token. Mutated only under the queue lock. */
    @Column(name = "last_order", nullable = false)
    private long lastOrder;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private long version;
}
