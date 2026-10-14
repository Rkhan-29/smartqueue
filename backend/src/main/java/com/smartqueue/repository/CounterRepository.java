package com.smartqueue.repository;

import com.smartqueue.entity.Counter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CounterRepository extends JpaRepository<Counter, UUID> {

    List<Counter> findByQueueIdOrderByNameAsc(UUID queueId);

    List<Counter> findByQueueIdAndActiveTrueOrderByNameAsc(UUID queueId);

    Optional<Counter> findByIdAndQueueId(UUID id, UUID queueId);

    long countByQueueIdAndActiveTrue(UUID queueId);
}
