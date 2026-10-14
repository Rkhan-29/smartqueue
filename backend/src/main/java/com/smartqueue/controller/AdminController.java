package com.smartqueue.controller;

import com.smartqueue.dto.AdminDtos.AdminTokenRow;
import com.smartqueue.dto.AdminDtos.AuditRow;
import com.smartqueue.dto.AdminDtos.BranchDto;
import com.smartqueue.dto.AdminDtos.BranchRequest;
import com.smartqueue.dto.AdminDtos.CounterRequest;
import com.smartqueue.dto.AdminDtos.DepartmentDto;
import com.smartqueue.dto.AdminDtos.DepartmentRequest;
import com.smartqueue.dto.AdminDtos.OrganizationDto;
import com.smartqueue.dto.AdminDtos.OrganizationRequest;
import com.smartqueue.dto.AdminDtos.PageDto;
import com.smartqueue.dto.AdminDtos.QueueAdminDto;
import com.smartqueue.dto.AdminDtos.QueueConfigDto;
import com.smartqueue.dto.AdminDtos.QueueCreateRequest;
import com.smartqueue.dto.AdminDtos.QueueUpdateRequest;
import com.smartqueue.dto.AdminDtos.ResetPasswordRequest;
import com.smartqueue.dto.AdminDtos.ServiceDto;
import com.smartqueue.dto.AdminDtos.ServiceRequest;
import com.smartqueue.dto.AdminDtos.SettingsDto;
import com.smartqueue.dto.AdminDtos.StaffCreateRequest;
import com.smartqueue.dto.AdminDtos.UserAdminDto;
import com.smartqueue.dto.AdminDtos.UserUpdateRequest;
import com.smartqueue.dto.AnalyticsDtos.AnalyticsReport;
import com.smartqueue.dto.AnalyticsDtos.DashboardStats;
import com.smartqueue.dto.QueueDtos.CounterDto;
import com.smartqueue.entity.TokenStatus;
import com.smartqueue.service.AdminService;
import com.smartqueue.service.AnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Administration API. Secured at URL level (/api/admin/** requires ADMIN). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService admin;
    private final AnalyticsService analytics;

    // ---- Dashboard & analytics ----
    @GetMapping("/dashboard")
    public DashboardStats dashboard() {
        return analytics.dashboard();
    }

    @GetMapping("/analytics")
    public AnalyticsReport analytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID queueId) {
        return analytics.report(from, to, queueId);
    }

    // ---- Organizations ----
    @GetMapping("/organizations")
    public List<OrganizationDto> organizations() {
        return admin.listOrganizations();
    }

    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationDto createOrganization(@Valid @RequestBody OrganizationRequest r) {
        return admin.createOrganization(r);
    }

    @PutMapping("/organizations/{id}")
    public OrganizationDto updateOrganization(@PathVariable UUID id, @Valid @RequestBody OrganizationRequest r) {
        return admin.updateOrganization(id, r);
    }

    // ---- Branches ----
    @GetMapping("/branches")
    public List<BranchDto> branches(@RequestParam(required = false) UUID organizationId) {
        return admin.listBranches(organizationId);
    }

    @PostMapping("/branches")
    @ResponseStatus(HttpStatus.CREATED)
    public BranchDto createBranch(@Valid @RequestBody BranchRequest r) {
        return admin.createBranch(r);
    }

    @PutMapping("/branches/{id}")
    public BranchDto updateBranch(@PathVariable UUID id, @Valid @RequestBody BranchRequest r) {
        return admin.updateBranch(id, r);
    }

    // ---- Departments ----
    @GetMapping("/departments")
    public List<DepartmentDto> departments(@RequestParam(required = false) UUID branchId) {
        return admin.listDepartments(branchId);
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentDto createDepartment(@Valid @RequestBody DepartmentRequest r) {
        return admin.createDepartment(r);
    }

    @PutMapping("/departments/{id}")
    public DepartmentDto updateDepartment(@PathVariable UUID id, @Valid @RequestBody DepartmentRequest r) {
        return admin.updateDepartment(id, r);
    }

    // ---- Services ----
    @GetMapping("/services")
    public List<ServiceDto> services(@RequestParam(required = false) UUID departmentId) {
        return admin.listServices(departmentId);
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceDto createService(@Valid @RequestBody ServiceRequest r) {
        return admin.createService(r);
    }

    @PutMapping("/services/{id}")
    public ServiceDto updateService(@PathVariable UUID id, @Valid @RequestBody ServiceRequest r) {
        return admin.updateService(id, r);
    }

    // ---- Queues ----
    @GetMapping("/queues")
    public List<QueueAdminDto> queues() {
        return admin.listQueues();
    }

    @GetMapping("/queues/{id}")
    public QueueAdminDto queue(@PathVariable UUID id) {
        return admin.getQueue(id);
    }

    @PostMapping("/queues")
    @ResponseStatus(HttpStatus.CREATED)
    public QueueAdminDto createQueue(@Valid @RequestBody QueueCreateRequest r) {
        return admin.createQueue(r);
    }

    @PutMapping("/queues/{id}")
    public QueueAdminDto updateQueue(@PathVariable UUID id, @Valid @RequestBody QueueUpdateRequest r) {
        return admin.updateQueue(id, r);
    }

    @PutMapping("/queues/{id}/config")
    public QueueAdminDto updateConfig(@PathVariable UUID id, @Valid @RequestBody QueueConfigDto r) {
        return admin.updateQueueConfig(id, r);
    }

    // ---- Counters ----
    @GetMapping("/queues/{queueId}/counters")
    public List<CounterDto> counters(@PathVariable UUID queueId) {
        return admin.listCounters(queueId);
    }

    @PostMapping("/queues/{queueId}/counters")
    @ResponseStatus(HttpStatus.CREATED)
    public CounterDto createCounter(@PathVariable UUID queueId, @Valid @RequestBody CounterRequest r) {
        return admin.createCounter(queueId, r);
    }

    @PutMapping("/queues/{queueId}/counters/{counterId}")
    public CounterDto updateCounter(@PathVariable UUID queueId, @PathVariable UUID counterId,
                                    @Valid @RequestBody CounterRequest r) {
        return admin.updateCounter(queueId, counterId, r);
    }

    // ---- Users ----
    @GetMapping("/staff")
    public List<UserAdminDto> staff() {
        return admin.listStaff();
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public UserAdminDto createStaff(@Valid @RequestBody StaffCreateRequest r) {
        return admin.createStaff(r);
    }

    @PutMapping("/users/{id}")
    public UserAdminDto updateUser(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest r) {
        return admin.updateUser(id, r);
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest r) {
        admin.resetPassword(id, r);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/customers")
    public PageDto<UserAdminDto> customers(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return admin.listCustomers(page, size);
    }

    // ---- Tokens, audit, settings ----
    @GetMapping("/tokens")
    public PageDto<AdminTokenRow> tokens(
            @RequestParam(required = false) UUID queueId,
            @RequestParam(required = false) TokenStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return admin.listTokens(queueId, status, date, page, size);
    }

    @GetMapping("/audit-logs")
    public PageDto<AuditRow> audit(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return admin.listAudit(action, entityType, from, to, page, size);
    }

    @GetMapping("/settings")
    public SettingsDto settings() {
        return admin.settings();
    }
}
