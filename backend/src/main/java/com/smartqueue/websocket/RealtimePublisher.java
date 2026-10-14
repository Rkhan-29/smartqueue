package com.smartqueue.websocket;

import com.smartqueue.dto.QueueDtos.QueueEvent;
import com.smartqueue.dto.QueueDtos.TokenEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes STOMP messages. Payloads are built inside the transaction, but are only sent after it
 * commits, so clients never see changes that were rolled back.
 *
 * Topics:
 *   /topic/queue/{publicRef}  - queue-level snapshot (no personal data)
 *   /topic/token/{reference}  - events for one token; the unguessable reference is the capability
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimePublisher {

    public static final String QUEUE_TOPIC = "/topic/queue/";
    public static final String TOKEN_TOPIC = "/topic/token/";

    private final SimpMessagingTemplate template;

    public void queueEvent(String publicRef, QueueEvent event) {
        afterCommit(() -> template.convertAndSend(QUEUE_TOPIC + publicRef, event));
    }

    public void tokenEvent(String reference, TokenEvent event) {
        afterCommit(() -> template.convertAndSend(TOKEN_TOPIC + reference, event));
    }

    private void afterCommit(Runnable task) {
        Runnable safe = () -> {
            try {
                task.run();
            } catch (Exception e) {
                // A failed broadcast must never fail or roll back the queue operation itself.
                log.warn("Realtime publish failed: {}", e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
        } else {
            safe.run();
        }
    }
}
