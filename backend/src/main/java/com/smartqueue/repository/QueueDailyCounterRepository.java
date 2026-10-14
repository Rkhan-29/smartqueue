package com.smartqueue.repository;

import com.smartqueue.entity.QueueDailyCounter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface QueueDailyCounterRepository extends JpaRepository<QueueDailyCounter, UUID> {

    Optional<QueueDailyCounter> findByQueueIdAndServiceDateAndPriority(UUID queueId, LocalDate serviceDate,
                                                                       boolean priority);
}
