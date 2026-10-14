package com.smartqueue.service;

import com.smartqueue.dto.AnalyticsDtos.AnalyticsReport;
import com.smartqueue.dto.AnalyticsDtos.DashboardStats;
import com.smartqueue.dto.AnalyticsDtos.DayPoint;
import com.smartqueue.dto.AnalyticsDtos.HourBucket;
import com.smartqueue.dto.AnalyticsDtos.QueueLoad;
import com.smartqueue.dto.AnalyticsDtos.QueueRow;
import com.smartqueue.exception.ApiException;
import com.smartqueue.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate reporting. All figures are computed in SQL; "today" and hours follow each branch's own time zone.
 * Pure read-only queries, so they never take the queue locks used by the engine.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final int MAX_RANGE_DAYS = 366;

    private final JdbcTemplate jdbc;

    /** "Today" in the branch's zone, as a SQL expression over aliases t (queue_tokens) and b (branches). */
    private static final String LOCAL_TODAY = "(now() at time zone b.time_zone)::date";

    private static final String JOINS = """
            from queue_tokens t
            join queues q on q.id = t.queue_id
            join services s on s.id = q.service_id
            join departments d on d.id = s.department_id
            join branches b on b.id = d.branch_id
            """;

    public DashboardStats dashboard() {
        // Headline counts for "today", evaluated per branch time zone
        var totals = jdbc.queryForMap("""
                select count(*) as total,
                       count(*) filter (where t.status = 'COMPLETED') as completed,
                       count(*) filter (where t.status in ('WAITING')) as waiting,
                       count(*) filter (where t.status = 'CANCELLED') as cancelled,
                       count(*) filter (where t.status = 'NO_SHOW') as no_show,
                       avg(extract(epoch from (coalesce(t.called_at, t.serving_at) - t.created_at)))
                           filter (where coalesce(t.called_at, t.serving_at) is not null) as avg_wait,
                       avg(extract(epoch from (t.completed_at - coalesce(t.serving_at, t.called_at))))
                           filter (where t.status = 'COMPLETED' and coalesce(t.serving_at, t.called_at) is not null) as avg_service
                """ + JOINS + " where t.service_date = " + LOCAL_TODAY);

        List<HourBucket> peak = jdbc.query("""
                select extract(hour from (t.created_at at time zone b.time_zone))::int as hr, count(*) as n
                """ + JOINS + " where t.service_date = " + LOCAL_TODAY + " group by hr order by hr",
                (rs, i) -> new HourBucket(rs.getInt("hr"), rs.getLong("n")));

        List<QueueLoad> loads = jdbc.query("""
                select q.id, q.name,
                       count(*) filter (where t.status = 'WAITING') as waiting,
                       count(*) filter (where t.status in ('CALLED','SERVING')) as in_progress,
                       count(*) filter (where t.status = 'COMPLETED') as completed,
                       avg(extract(epoch from (coalesce(t.called_at, t.serving_at) - t.created_at)))
                           filter (where coalesce(t.called_at, t.serving_at) is not null) as avg_wait,
                       avg(extract(epoch from (t.completed_at - coalesce(t.serving_at, t.called_at))))
                           filter (where t.status = 'COMPLETED' and coalesce(t.serving_at, t.called_at) is not null) as avg_service,
                       (select greatest(1, count(*)) from counters c where c.queue_id = q.id and c.active) as servers
                from queues q
                join services s on s.id = q.service_id
                join departments d on d.id = s.department_id
                join branches b on b.id = d.branch_id
                left join queue_tokens t on t.queue_id = q.id and t.service_date = (now() at time zone b.time_zone)::date
                where q.active
                group by q.id, q.name
                order by q.name
                """,
                (rs, i) -> {
                    long inProgress = rs.getLong("in_progress");
                    long servers = Math.max(1, rs.getLong("servers"));
                    return new QueueLoad(rs.getObject("id", UUID.class), rs.getString("name"),
                            rs.getLong("waiting"), inProgress, rs.getLong("completed"),
                            minutes(rs.getDouble("avg_wait")), minutes(rs.getDouble("avg_service")),
                            round1(Math.min(100.0, 100.0 * inProgress / servers)));
                });

        double utilization = loads.isEmpty() ? 0
                : round1(loads.stream().mapToDouble(QueueLoad::utilizationPercent).average().orElse(0));
        return new DashboardStats(
                num(totals.get("total")), num(totals.get("completed")), num(totals.get("waiting")),
                num(totals.get("cancelled")), num(totals.get("no_show")),
                minutes(dbl(totals.get("avg_wait"))), minutes(dbl(totals.get("avg_service"))),
                utilization, loads.size(), peak, loads);
    }

    public AnalyticsReport report(LocalDate from, LocalDate to, UUID queueId) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(13);
        if (start.isAfter(end) || start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Choose a date range of at most one year.");
        }
        String queueFilter = queueId == null ? "" : " and t.queue_id = ?";
        Object[] args = queueId == null ? new Object[]{Date.valueOf(start), Date.valueOf(end)}
                : new Object[]{Date.valueOf(start), Date.valueOf(end), queueId};

        List<DayPoint> daily = jdbc.query("""
                select t.service_date as day, count(*) as total,
                       count(*) filter (where t.status = 'COMPLETED') as completed,
                       count(*) filter (where t.status = 'CANCELLED') as cancelled,
                       count(*) filter (where t.status = 'NO_SHOW') as no_show,
                       avg(extract(epoch from (coalesce(t.called_at, t.serving_at) - t.created_at)))
                           filter (where coalesce(t.called_at, t.serving_at) is not null) as avg_wait
                from queue_tokens t
                where t.service_date between ? and ?""" + queueFilter + " group by t.service_date order by t.service_date",
                (rs, i) -> new DayPoint(rs.getDate("day").toLocalDate(), rs.getLong("total"),
                        rs.getLong("completed"), rs.getLong("cancelled"), rs.getLong("no_show"),
                        minutes(rs.getDouble("avg_wait"))), args);

        List<HourBucket> hourly = jdbc.query("""
                select extract(hour from (t.created_at at time zone b.time_zone))::int as hr, count(*) as n
                """ + JOINS + " where t.service_date between ? and ?" + queueFilter + " group by hr order by hr",
                (rs, i) -> new HourBucket(rs.getInt("hr"), rs.getLong("n")), args);

        List<QueueRow> byQueue = jdbc.query("""
                select q.id, q.name, count(*) as total,
                       count(*) filter (where t.status = 'COMPLETED') as completed,
                       count(*) filter (where t.status = 'CANCELLED') as cancelled,
                       avg(extract(epoch from (coalesce(t.called_at, t.serving_at) - t.created_at)))
                           filter (where coalesce(t.called_at, t.serving_at) is not null) as avg_wait,
                       avg(extract(epoch from (t.completed_at - coalesce(t.serving_at, t.called_at))))
                           filter (where t.status = 'COMPLETED' and coalesce(t.serving_at, t.called_at) is not null) as avg_service
                """ + JOINS + " where t.service_date between ? and ?" + queueFilter
                + " group by q.id, q.name order by total desc",
                (rs, i) -> new QueueRow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getLong("total"),
                        rs.getLong("completed"), rs.getLong("cancelled"), minutes(rs.getDouble("avg_wait")),
                        minutes(rs.getDouble("avg_service"))), args);

        return new AnalyticsReport(start, end, daily, hourly, byQueue);
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static double dbl(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }

    /** Seconds to minutes, one decimal. */
    private static double minutes(double seconds) {
        return round1(seconds / 60.0);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
