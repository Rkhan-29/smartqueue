package com.smartqueue.service.notification;

import com.smartqueue.entity.Notification;
import com.smartqueue.entity.NotificationChannel;
import com.smartqueue.entity.QueueToken;
import com.smartqueue.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Persists an in-app notification; live delivery to the browser happens over the token WebSocket topic. */
@Component
@RequiredArgsConstructor
public class InAppNotificationSender implements NotificationSender {

    private final NotificationRepository repository;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public void send(QueueToken token, String type, String title, String message) {
        Notification n = new Notification();
        n.setToken(token);
        n.setUser(token.getUser());
        n.setChannel(NotificationChannel.IN_APP);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message.length() > 500 ? message.substring(0, 500) : message);
        repository.save(n);
    }
}
