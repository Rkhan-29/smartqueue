package com.smartqueue.dto;

import com.smartqueue.entity.PriorityMode;
import com.smartqueue.entity.QueueStatus;
import com.smartqueue.entity.Role;
import com.smartqueue.entity.TokenStatus;
import com.smartqueue.entity.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

/** Request/response models for the administration API. */
public final class AdminDtos {

    private AdminDtos() {
    }

    // ---- Organization / Branch / Department / Service ----

    public record OrganizationRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Use lowercase letters, numbers and dashes")
            @Size(max = 80) String slug,
            @Size(max = 500) String description,
            Boolean active) {
    }

    public record OrganizationDto(UUID id, String name, String slug, String description, boolean active) {
    }

    public record BranchRequest(
            @NotNull UUID organizationId,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 300) String address,
            @Size(max = 100) String city,
            @Size(max = 60) String timeZone,
            Boolean active) {
    }

    public record BranchDto(UUID id, UUID organizationId, String organizationName, String name, String address,
                            String city, String timeZone, boolean active) {
    }

    public record DepartmentRequest(
            @NotNull UUID branchId,
            @NotBlank @Size(max = 150) String name,
            Boolean active) {
    }

    public record DepartmentDto(UUID id, UUID branchId, String branchName, String name, boolean active) {
    }

    public record ServiceRequest(
            @NotNull UUID departmentId,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String description,
            Boolean active) {
    }

    public record ServiceDto(UUID id, UUID departmentId, String departmentName, String branchName, String name,
                             String description, boolean active) {
    }

    // ---- Queues ----

    public record QueueConfigDto(
            @NotBlank @Pattern(regexp = "^[A-Z0-9]{1,6}$", message = "1-6 uppercase letters or digits") String tokenPrefix,
            @NotBlank @Pattern(regexp = "^[A-Z0-9]{1,6}$", message = "1-6 uppercase letters or digits") String priorityPrefix,
            @NotNull @Min(0) @Max(99_999) Integer startingNumber,
            @NotNull @Min(1) @Max(8) Integer numberPadding,
            @NotNull @Min(1) @Max(5_000) Integer maxQueueSize,
            @NotNull PriorityMode priorityMode,
            @NotNull @Min(1) @Max(50) Integer priorityAfterN,
            @NotNull @Min(1) @Max(480) Integer avgServiceMinutes,
            LocalTime openingTime,
            LocalTime closingTime,
            LocalTime breakStart,
            LocalTime breakEnd,
            @NotNull @Min(1) @Max(1_440) Integer autoExpireMinutes,
            @NotNull @Min(1) @Max(120) Integer noShowTimeoutMinutes,
            @NotNull Boolean allowDuplicateActive,
            @NotNull Boolean directServing,
            @NotNull @Min(0) @Max(20) Integer nearTurnThreshold) {
    }

    public record QueueCreateRequest(
            @NotNull UUID serviceId,
            @NotBlank @Size(max = 150) String name,
            @Valid QueueConfigDto config) {
    }

    public record QueueUpdateRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull QueueStatus status,
            Boolean active) {
    }

    public record QueueAdminDto(
            UUID id, String publicRef, String name, QueueStatus status, boolean active,
            UUID serviceId, String serviceName, String departmentName, String branchName, String organizationName,
            QueueConfigDto config, long waitingCount, String joinUrl) {
    }

    public record CounterRequest(
            @NotBlank @Size(max = 80) String name,
            Boolean active) {
    }

    // ---- Users ----

    public record StaffCreateRequest(
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,60}$", message = "3-60 letters, numbers, dot, dash or underscore") String username,
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_REGEX, message = AuthDtos.PASSWORD_MESSAGE) String password,
            @NotNull Role role,
            UUID organizationId,
            Set<UUID> queueIds) {
    }

    public record UserUpdateRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotNull Role role,
            @NotNull UserStatus status,
            Set<UUID> queueIds) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_REGEX, message = AuthDtos.PASSWORD_MESSAGE) String newPassword) {
    }

    public record UserAdminDto(UUID id, String email, String username, String fullName, Role role,
                               UserStatus status, UUID organizationId, Instant lastLoginAt,
                               Set<UUID> queueIds, Instant createdAt) {
    }

    // ---- Tokens / audit ----

    public record AdminTokenRow(UUID id, String tokenNumber, TokenStatus status, boolean priority,
                                UUID queueId, String queueName, String customerName, boolean guest,
                                LocalDate serviceDate, Instant createdAt, Instant calledAt, Instant completedAt) {
    }

    public record AuditRow(UUID id, String actor, String action, String entityType, String entityId,
                           String ipAddress, String metadata, Instant createdAt) {
    }

    public record PageDto<T>(java.util.List<T> items, int page, int size, long totalItems, int totalPages) {
    }

    public record SettingsDto(String publicBaseUrl, int accessTokenTtlMinutes, int refreshTokenTtlDays,
                              int loginMaxAttempts, int loginLockoutMinutes, int authRateLimitPerMinute,
                              int joinRateLimitPerMinute, boolean demoDataEnabled) {
    }
}
