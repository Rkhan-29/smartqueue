package com.smartqueue.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/** Per-queue/per-day token number sequence. Only mutated while the queue row is locked. */
@Entity
@Table(name = "queue_daily_counters")
@Getter
@Setter
public class QueueDailyCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "queue_id", nullable = false)
    private UUID queueId;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(nullable = false)
    private boolean priority;

    @Column(name = "last_number", nullable = false)
    private int lastNumber;
}
