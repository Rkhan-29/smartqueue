package com.smartqueue.service.notification;

import com.smartqueue.dto.NotificationDto;
import com.smartqueue.entity.QueueToken;
import com.smartqueue.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class NotificationService {

    private final List<NotificationSender> senders;
    private final NotificationRepository repository;

    public NotificationService(List<NotificationSender> senders, NotificationRepository repository) {
        this.senders = senders;
        this.repository = repository;
    }

    /** Fans a notification out to every registered channel. A failing channel never breaks the others. */
    public void dispatch(QueueToken token, String type, String title, String message) {
        for (NotificationSender sender : senders) {
            try {
                sender.send(token, type, title, message);
            } catch (RuntimeException e) {
                log.warn("Notification channel {} failed: {}", sender.channel(), e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> recentForToken(UUID tokenId, int limit) {
        return repository.findByTokenIdOrderByCreatedAtDesc(tokenId, PageRequest.of(0, limit)).stream()
                .map(n -> new NotificationDto(n.getType(), n.getTitle(), n.getMessage(), n.getCreatedAt()))
                .toList();
    }
}
