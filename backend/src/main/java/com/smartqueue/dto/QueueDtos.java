package com.smartqueue.dto;

import com.smartqueue.entity.QueueStatus;
import com.smartqueue.entity.TokenStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Customer- and staff-facing queue/token view models. Entities are never serialised directly. */
public final class QueueDtos {

    private QueueDtos() {
    }

    public enum EstimateConfidence {LOW, MEDIUM, HIGH}

    public record ServingEntry(String tokenNumber, String counterName, TokenStatus status) {
    }

    /** Public, PII-free picture of a queue. Also the payload broadcast on the queue topic. */
    public record QueueSnapshot(
            UUID queueId,
            String publicRef,
            String name,
            QueueStatus status,
            String serviceName,
            String departmentName,
            String branchName,
            String organizationName,
            long waitingCount,
            List<ServingEntry> nowServing,
            String lastCalledToken,
            double avgServiceMinutes,
            int estimatedWaitForNewTokenMinutes,
            EstimateConfidence confidence,
            boolean joinable,
            String unavailableReason,
            boolean priorityEnabled,
            Instant updatedAt) {
    }

    public record JoinRequest(
            @Size(max = 100) String customerName,
            Boolean priority) {
        public boolean priorityRequested() {
            return Boolean.TRUE.equals(priority);
        }
    }

    public record TokenView(
            String reference,
            String tokenNumber,
            TokenStatus status,
            boolean priority,
            UUID queueId,
            String queuePublicRef,
            String queueName,
            QueueStatus queueStatus,
            String serviceName,
            String branchName,
            String organizationName,
            String currentlyServing,
            int peopleAhead,
            int position,
            Integer estimatedWaitMinutes,
            EstimateConfidence confidence,
            String counterName,
            boolean nearTurn,
            String message,
            Instant joinedAt,
            Instant calledAt,
            Instant updatedAt,
            Instant serverTime) {
    }

    public record JoinResponse(TokenView token, String sessionId) {
    }

    public record StaffTokenDto(
            UUID id,
            String tokenNumber,
            TokenStatus status,
            boolean priority,
            String customerName,
            UUID counterId,
            String counterName,
            Instant createdAt,
            Instant calledAt,
            Instant servingAt,
            Instant completedAt,
            long waitingSeconds) {
    }

    public record QueueStatsDto(
            long waiting,
            long called,
            long serving,
            long completedToday,
            long cancelledToday,
            long skippedToday,
            long noShowToday,
            long totalToday,
            double avgWaitMinutes,
            double avgServiceMinutes) {
    }

    public record CounterDto(UUID id, String name, boolean active, String currentToken) {
    }

    public record StaffQueueState(
            QueueSnapshot queue,
            QueueStatsDto stats,
            List<StaffTokenDto> active,
            List<StaffTokenDto> waiting,
            List<StaffTokenDto> recent,
            StaffTokenDto nextToken,
            List<CounterDto> counters) {
    }

    /** Lightweight entry in the list of queues a staff member can operate. */
    public record StaffQueueSummary(UUID queueId, String publicRef, String name, QueueStatus status,
                                    String serviceName, String branchName, long waitingCount) {
    }

    // ---- Requests ----

    public record NextRequest(UUID counterId) {
    }

    public record CounterAssignRequest(UUID counterId) {
    }

    public record MoveRequest(@Min(1) int position) {
    }

    // ---- WebSocket payloads ----

    public record QueueEvent(String type, QueueSnapshot queue, String tokenNumber, Instant at) {
    }

    public record TokenEvent(String type, TokenView token, String title, String message, Instant at) {
    }
}
