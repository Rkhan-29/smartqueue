package com.smartqueue.repository;

import com.smartqueue.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByTokenIdOrderByCreatedAtDesc(UUID tokenId, Pageable pageable);
}
