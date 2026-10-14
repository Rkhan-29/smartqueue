package com.smartqueue.repository;

import com.smartqueue.entity.QueueConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueConfigurationRepository extends JpaRepository<QueueConfiguration, UUID> {

    Optional<QueueConfiguration> findByQueueId(UUID queueId);

    @org.springframework.data.jpa.repository.Query(
            "select c from QueueConfiguration c join fetch c.queue q where q.active = true")
    List<QueueConfiguration> findAllWithActiveQueue();

    List<QueueConfiguration> findByQueueIdIn(Collection<UUID> queueIds);
}
