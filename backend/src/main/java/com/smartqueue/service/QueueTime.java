package com.smartqueue.service;

import com.smartqueue.entity.QueueConfiguration;
import com.smartqueue.entity.ServiceQueue;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Branch-local time helpers; a queue's "day" and working hours follow its branch time zone. */
public final class QueueTime {

    private QueueTime() {
    }

    public static ZoneId zone(ServiceQueue queue) {
        String id = queue.getService().getDepartment().getBranch().getTimeZone();
        try {
            return ZoneId.of(id);
        } catch (DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }

    public static LocalDate today(ServiceQueue queue) {
        return LocalDate.now(zone(queue));
    }

    public static LocalTime nowTime(ServiceQueue queue) {
        return LocalTime.now(zone(queue)).withNano(0);
    }

    public static LocalDate dateOf(ServiceQueue queue, Instant instant) {
        return instant.atZone(zone(queue)).toLocalDate();
    }

    public static boolean withinWorkingHours(QueueConfiguration cfg, LocalTime now) {
        LocalTime open = cfg.getOpeningTime();
        LocalTime close = cfg.getClosingTime();
        if (open == null || close == null) {
            return true;
        }
        if (open.isBefore(close)) {
            return !now.isBefore(open) && now.isBefore(close);
        }
        return !now.isBefore(open) || now.isBefore(close); // overnight window
    }

    public static boolean onBreak(QueueConfiguration cfg, LocalTime now) {
        LocalTime start = cfg.getBreakStart();
        LocalTime end = cfg.getBreakEnd();
        if (start == null || end == null) {
            return false;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }
}
