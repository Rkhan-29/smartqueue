package com.smartqueue.dto;

import java.time.Instant;

public record NotificationDto(String type, String title, String message, Instant createdAt) {
}
