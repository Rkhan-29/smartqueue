package com.smartqueue.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record HourBucket(int hour, long tokens) {
    }

    public record DayPoint(LocalDate date, long total, long completed, long cancelled, long noShow,
                           double avgWaitMinutes) {
    }

    public record QueueLoad(UUID queueId, String name, long waiting, long inProgress, long completedToday,
                            double avgWaitMinutes, double avgServiceMinutes, double utilizationPercent) {
    }

    /** Headline numbers for the admin dashboard (today, in each branch's local time). */
    public record DashboardStats(long totalTokensToday, long completedToday, long waitingNow, long cancelledToday,
                                 long noShowToday, double avgWaitMinutes, double avgServiceMinutes,
                                 double queueUtilizationPercent, int activeQueues,
                                 List<HourBucket> peakHours, List<QueueLoad> queues) {
    }

    public record QueueRow(UUID queueId, String name, long total, long completed, long cancelled,
                           double avgWaitMinutes, double avgServiceMinutes) {
    }

    public record AnalyticsReport(LocalDate from, LocalDate to, List<DayPoint> daily, List<HourBucket> hourly,
                                  List<QueueRow> byQueue) {
    }
}
