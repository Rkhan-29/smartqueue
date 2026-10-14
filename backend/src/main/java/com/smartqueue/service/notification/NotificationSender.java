package com.smartqueue.service.notification;

import com.smartqueue.entity.NotificationChannel;
import com.smartqueue.entity.QueueToken;

/**
 * Pluggable delivery channel. The initial release ships {@link InAppNotificationSender};
 * add Email/SMS beans implementing this interface and they are picked up automatically.
 */
public interface NotificationSender {

    NotificationChannel channel();

    void send(QueueToken token, String type, String title, String message);
}
