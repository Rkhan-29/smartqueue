package com.smartqueue.controller;

import com.smartqueue.dto.QueueDtos.QueueStatsDto;
import com.smartqueue.dto.QueueDtos.StaffQueueState;
import com.smartqueue.dto.QueueDtos.StaffQueueSummary;
import com.smartqueue.service.StaffQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
@PreAuthorize("hasAnyRole('STAFF','ADMIN')")
@RequiredArgsConstructor
public class StaffController {

    private final StaffQueueService staffQueues;

    @GetMapping("/queues")
    public List<StaffQueueSummary> myQueues() {
        return staffQueues.myQueues();
    }

    @GetMapping("/queues/{queueId}/state")
    public StaffQueueState state(@PathVariable UUID queueId) {
        return staffQueues.state(queueId);
    }

    @GetMapping("/queues/{queueId}/stats")
    public QueueStatsDto stats(@PathVariable UUID queueId) {
        return staffQueues.stats(queueId);
    }
}
