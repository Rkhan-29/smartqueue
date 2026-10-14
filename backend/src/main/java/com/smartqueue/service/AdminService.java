package com.smartqueue.service;

import com.smartqueue.config.AppProperties;
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
import com.smartqueue.dto.QueueDtos.CounterDto;
import com.smartqueue.entity.AppUser;
import com.smartqueue.entity.AuditLog;
import com.smartqueue.entity.Branch;
import com.smartqueue.entity.Counter;
import com.smartqueue.entity.Department;
import com.smartqueue.entity.Organization;
import com.smartqueue.entity.PriorityMode;
import com.smartqueue.entity.QueueConfiguration;
import com.smartqueue.entity.QueueStatus;
import com.smartqueue.entity.QueueToken;
import com.smartqueue.entity.Role;
import com.smartqueue.entity.ServiceOffering;
import com.smartqueue.entity.ServiceQueue;
import com.smartqueue.entity.TokenStatus;
import com.smartqueue.entity.UserStatus;
import com.smartqueue.exception.ApiException;
import com.smartqueue.exception.ErrorCode;
import com.smartqueue.repository.AppUserRepository;
import com.smartqueue.repository.AuditLogRepository;
import com.smartqueue.repository.BranchRepository;
import com.smartqueue.repository.CounterRepository;
import com.smartqueue.repository.DepartmentRepository;
import com.smartqueue.repository.OrganizationRepository;
import com.smartqueue.repository.QueueConfigurationRepository;
import com.smartqueue.repository.QueueTokenRepository;
import com.smartqueue.repository.ServiceOfferingRepository;
import com.smartqueue.repository.ServiceQueueRepository;
import com.smartqueue.security.AuthenticatedUser;
import com.smartqueue.security.CurrentUser;
import com.smartqueue.security.TokenHasher;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Administration use-cases. Every change is audited in the same transaction. */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {

    private final OrganizationRepository organizations;
    private final BranchRepository branches;
    private final DepartmentRepository departments;
    private final ServiceOfferingRepository services;
    private final ServiceQueueRepository queues;
    private final QueueConfigurationRepository configs;
    private final CounterRepository counters;
    private final AppUserRepository users;
    private final QueueTokenRepository tokens;
    private final AuditLogRepository auditLogs;
    private final AuditService audit;
    private final AuthService authService;
    private final PasswordEncoder encoder;
    private final QrCodeService qr;
    private final AppProperties props;
    private final EstimationService estimation;
    private final com.smartqueue.websocket.RealtimePublisher realtime;
    private final QueueViewService views;

    // ================================================================ organizations

    @Transactional(readOnly = true)
    public List<OrganizationDto> listOrganizations() {
        return organizations.findAll(Sort.by("name")).stream().map(AdminService::toDto).toList();
    }

    public OrganizationDto createOrganization(OrganizationRequest r) {
        String slug = r.slug().trim().toLowerCase();
        if (organizations.existsBySlugIgnoreCase(slug)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "That slug is already in use.");
        }
        Organization o = new Organization();
        applyOrg(o, r, slug);
        organizations.save(o);
        audit.record("ORGANIZATION_CREATED", "ORGANIZATION", o.getId(), Map.of("name", o.getName()));
        return toDto(o);
    }

    public OrganizationDto updateOrganization(UUID id, OrganizationRequest r) {
        Organization o = organizations.findById(id).orElseThrow(this::notFound);
        String slug = r.slug().trim().toLowerCase();
        if (organizations.existsBySlugIgnoreCaseAndIdNot(slug, id)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "That slug is already in use.");
        }
        applyOrg(o, r, slug);
        audit.record("ORGANIZATION_UPDATED", "ORGANIZATION", id, Map.of("name", o.getName()));
        return toDto(o);
    }

    private static void applyOrg(Organization o, OrganizationRequest r, String slug) {
        o.setName(r.name().trim());
        o.setSlug(slug);
        o.setDescription(r.description());
        if (r.active() != null) {
            o.setActive(r.active());
        }
    }

    // ================================================================ branches

    @Transactional(readOnly = true)
    public List<BranchDto> listBranches(UUID organizationId) {
        List<Branch> list = organizationId == null ? branches.findAllByOrderByNameAsc()
                : branches.findByOrganizationIdOrderByNameAsc(organizationId);
        return list.stream().map(PublicCatalogService::toDto).toList();
    }

    public BranchDto createBranch(BranchRequest r) {
        Branch b = new Branch();
        b.setOrganization(organizations.findById(r.organizationId()).orElseThrow(this::notFound));
        applyBranch(b, r);
        branches.save(b);
        audit.record("BRANCH_CREATED", "BRANCH", b.getId(), Map.of("name", b.getName()));
        return PublicCatalogService.toDto(b);
    }

    public BranchDto updateBranch(UUID id, BranchRequest r) {
        Branch b = branches.findById(id).orElseThrow(this::notFound);
        applyBranch(b, r);
        audit.record("BRANCH_UPDATED", "BRANCH", id, Map.of("name", b.getName()));
        return PublicCatalogService.toDto(b);
    }

    private void applyBranch(Branch b, BranchRequest r) {
        b.setName(r.name().trim());
        b.setAddress(r.address());
        b.setCity(r.city());
        if (r.timeZone() != null && !r.timeZone().isBlank()) {
            try {
                ZoneId.of(r.timeZone());
            } catch (DateTimeException e) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Unknown time zone.");
            }
            b.setTimeZone(r.timeZone());
        }
        if (r.active() != null) {
            b.setActive(r.active());
        }
    }

    // ================================================================ departments

    @Transactional(readOnly = true)
    public List<DepartmentDto> listDepartments(UUID branchId) {
        List<Department> list = branchId == null ? departments.findAllByOrderByNameAsc()
                : departments.findByBranchIdOrderByNameAsc(branchId);
        return list.stream().map(AdminService::toDto).toList();
    }

    public DepartmentDto createDepartment(DepartmentRequest r) {
        Department d = new Department();
        d.setBranch(branches.findById(r.branchId()).orElseThrow(this::notFound));
        d.setName(r.name().trim());
        if (r.active() != null) {
            d.setActive(r.active());
        }
        departments.save(d);
        audit.record("DEPARTMENT_CREATED", "DEPARTMENT", d.getId(), Map.of("name", d.getName()));
        return toDto(d);
    }

    public DepartmentDto updateDepartment(UUID id, DepartmentRequest r) {
        Department d = departments.findById(id).orElseThrow(this::notFound);
        d.setName(r.name().trim());
        if (r.active() != null) {
            d.setActive(r.active());
        }
        audit.record("DEPARTMENT_UPDATED", "DEPARTMENT", id, Map.of("name", d.getName()));
        return toDto(d);
    }

    // ================================================================ services

    @Transactional(readOnly = true)
    public List<ServiceDto> listServices(UUID departmentId) {
        List<ServiceOffering> list = departmentId == null ? services.findAllByOrderByNameAsc()
                : services.findByDepartmentIdOrderByNameAsc(departmentId);
        return list.stream().map(AdminService::toDto).toList();
    }

    public ServiceDto createService(ServiceRequest r) {
        ServiceOffering s = new ServiceOffering();
        s.setDepartment(departments.findById(r.departmentId()).orElseThrow(this::notFound));
        s.setName(r.name().trim());
        s.setDescription(r.description());
        if (r.active() != null) {
            s.setActive(r.active());
        }
        services.save(s);
        audit.record("SERVICE_CREATED", "SERVICE", s.getId(), Map.of("name", s.getName()));
        return toDto(s);
    }

    public ServiceDto updateService(UUID id, ServiceRequest r) {
        ServiceOffering s = services.findById(id).orElseThrow(this::notFound);
        s.setName(r.name().trim());
        s.setDescription(r.description());
        if (r.active() != null) {
            s.setActive(r.active());
        }
        audit.record("SERVICE_UPDATED", "SERVICE", id, Map.of("name", s.getName()));
        return toDto(s);
    }

    // ================================================================ queues

    @Transactional(readOnly = true)
    public List<QueueAdminDto> listQueues() {
        List<ServiceQueue> list = queues.findAllByOrderByNameAsc();
        Map<UUID, QueueConfiguration> cfgs = configs.findByQueueIdIn(list.stream().map(ServiceQueue::getId).toList())
                .stream().collect(Collectors.toMap(c -> c.getQueue().getId(), Function.identity()));
        return list.stream().map(q -> toAdminDto(q, cfgs.get(q.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public QueueAdminDto getQueue(UUID id) {
        ServiceQueue q = queues.findWithDetailsById(id).orElseThrow(() -> new ApiException(ErrorCode.QUEUE_NOT_FOUND));
        return toAdminDto(q, requireConfig(id));
    }

    public QueueAdminDto createQueue(QueueCreateRequest r) {
        ServiceOffering service = services.findById(r.serviceId()).orElseThrow(this::notFound);
        ServiceQueue q = new ServiceQueue();
        q.setService(service);
        q.setName(r.name().trim());
        q.setPublicRef(newPublicRef());
        queues.save(q);

        QueueConfiguration cfg = new QueueConfiguration();
        cfg.setQueue(q);
        if (r.config() != null) {
            applyConfig(cfg, r.config());
        }
        configs.save(cfg);
        audit.record("QUEUE_CREATED", "QUEUE", q.getId(), Map.of("name", q.getName()));
        return toAdminDto(queues.findWithDetailsById(q.getId()).orElseThrow(), cfg);
    }

    public QueueAdminDto updateQueue(UUID id, QueueUpdateRequest r) {
        ServiceQueue q = queues.lockById(id).orElseThrow(() -> new ApiException(ErrorCode.QUEUE_NOT_FOUND));
        q.setName(r.name().trim());
        q.setStatus(r.status());
        if (r.active() != null) {
            q.setActive(r.active());
        }
        audit.record("QUEUE_UPDATED", "QUEUE", id, Map.of("name", q.getName(), "status", q.getStatus().name()));
        ServiceQueue detailed = queues.findWithDetailsById(id).orElseThrow();
        QueueConfiguration cfg = requireConfig(id);
        broadcastQueueChange(detailed, cfg);
        return toAdminDto(detailed, cfg);
    }

    public QueueAdminDto updateQueueConfig(UUID id, QueueConfigDto dto) {
        ServiceQueue q = queues.lockById(id).orElseThrow(() -> new ApiException(ErrorCode.QUEUE_NOT_FOUND));
        QueueConfiguration cfg = requireConfig(id);
        applyConfig(cfg, dto);
        q.setPriorityStreak(0);
        estimation.evict(id);
        audit.record("QUEUE_SETTINGS_CHANGED", "QUEUE", id, Map.of("queue", q.getName()));
        ServiceQueue detailed = queues.findWithDetailsById(id).orElseThrow();
        broadcastQueueChange(detailed, cfg);
        return toAdminDto(detailed, cfg);
    }

    /** Lets open customer/staff screens pick up admin changes (pause, close, new rules) immediately. */
    private void broadcastQueueChange(ServiceQueue q, QueueConfiguration cfg) {
        realtime.queueEvent(q.getPublicRef(),
                new com.smartqueue.dto.QueueDtos.QueueEvent("QUEUE_UPDATED", views.snapshot(q, cfg), null, Instant.now()));
    }

    private void applyConfig(QueueConfiguration cfg, QueueConfigDto d) {
        if (d.tokenPrefix().equals(d.priorityPrefix())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Priority prefix must differ from the token prefix.");
        }
        cfg.setTokenPrefix(d.tokenPrefix());
        cfg.setPriorityPrefix(d.priorityPrefix());
        cfg.setStartingNumber(d.startingNumber());
        cfg.setNumberPadding(d.numberPadding());
        cfg.setMaxQueueSize(d.maxQueueSize());
        cfg.setPriorityMode(d.priorityMode());
        cfg.setPriorityAfterN(d.priorityAfterN());
        cfg.setAvgServiceMinutes(d.avgServiceMinutes());
        cfg.setOpeningTime(d.openingTime());
        cfg.setClosingTime(d.closingTime());
        cfg.setBreakStart(d.breakStart());
        cfg.setBreakEnd(d.breakEnd());
        cfg.setAutoExpireMinutes(d.autoExpireMinutes());
        cfg.setNoShowTimeoutMinutes(d.noShowTimeoutMinutes());
        cfg.setAllowDuplicateActive(d.allowDuplicateActive());
        cfg.setDirectServing(d.directServing());
        cfg.setNearTurnThreshold(d.nearTurnThreshold());
    }

    private String newPublicRef() {
        for (int i = 0; i < 5; i++) {
            String ref = TokenHasher.randomUrlSafe(9);
            if (!queues.existsByPublicRef(ref)) {
                return ref;
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR);
    }

    // ================================================================ counters

    @Transactional(readOnly = true)
    public List<CounterDto> listCounters(UUID queueId) {
        return counters.findByQueueIdOrderByNameAsc(queueId).stream()
                .map(c -> new CounterDto(c.getId(), c.getName(), c.isActive(), null)).toList();
    }

    public CounterDto createCounter(UUID queueId, CounterRequest r) {
        ServiceQueue q = queues.findById(queueId).orElseThrow(() -> new ApiException(ErrorCode.QUEUE_NOT_FOUND));
        Counter c = new Counter();
        c.setQueue(q);
        c.setName(r.name().trim());
        if (r.active() != null) {
            c.setActive(r.active());
        }
        counters.save(c);
        estimation.evict(queueId);
        audit.record("COUNTER_CREATED", "COUNTER", c.getId(), Map.of("name", c.getName(), "queue", q.getName()));
        return new CounterDto(c.getId(), c.getName(), c.isActive(), null);
    }

    public CounterDto updateCounter(UUID queueId, UUID counterId, CounterRequest r) {
        Counter c = counters.findByIdAndQueueId(counterId, queueId).orElseThrow(this::notFound);
        c.setName(r.name().trim());
        if (r.active() != null) {
            c.setActive(r.active());
        }
        estimation.evict(queueId);
        audit.record("COUNTER_UPDATED", "COUNTER", counterId, Map.of("name", c.getName()));
        return new CounterDto(c.getId(), c.getName(), c.isActive(), null);
    }

    // ================================================================ users

    @Transactional(readOnly = true)
    public List<UserAdminDto> listStaff() {
        return users.findByRoleInOrderByFullNameAsc(List.of(Role.STAFF, Role.ADMIN)).stream()
                .map(u -> toDto(u, assignedIds(u))).toList();
    }

    @Transactional(readOnly = true)
    public PageDto<UserAdminDto> listCustomers(int page, int size) {
        Page<AppUser> result = users.findAll(
                (root, query, cb) -> cb.equal(root.get("role"), Role.CUSTOMER),
                PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageDto<>(result.map(u -> toDto(u, Set.of())).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public UserAdminDto createStaff(StaffCreateRequest r) {
        if (r.role() == Role.CUSTOMER) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Use the public registration for customers.");
        }
        String email = r.email().trim().toLowerCase();
        if (users.emailExists(email)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "An account with this email already exists.");
        }
        if (users.usernameExists(r.username().trim())) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "That username is already taken.");
        }
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setUsername(r.username().trim());
        u.setFullName(r.fullName().trim());
        u.setPasswordHash(encoder.encode(r.password()));
        u.setRole(r.role());
        u.setStatus(UserStatus.ACTIVE);
        if (r.organizationId() != null) {
            u.setOrganization(organizations.findById(r.organizationId()).orElseThrow(this::notFound));
        }
        u.setAssignedQueues(resolveQueues(r.queueIds()));
        users.save(u);
        audit.record("STAFF_CREATED", "USER", u.getId(), Map.of("username", u.getUsername(), "role", u.getRole().name()));
        return toDto(u, assignedIds(u));
    }

    public UserAdminDto updateUser(UUID id, UserUpdateRequest r) {
        AppUser u = users.findWithAssignedQueuesById(id).orElseThrow(this::notFound);
        AuthenticatedUser actor = CurrentUser.require();
        if (u.getId().equals(actor.id()) && (r.status() != UserStatus.ACTIVE || r.role() != Role.ADMIN)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "You cannot disable or demote your own account.");
        }
        if (u.getRole() == Role.CUSTOMER && r.role() != Role.CUSTOMER) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Customers cannot be promoted to staff.");
        }
        boolean disabling = u.getStatus() == UserStatus.ACTIVE && r.status() == UserStatus.DISABLED;
        boolean roleChanged = u.getRole() != r.role();
        u.setFullName(r.fullName().trim());
        u.setRole(r.role());
        u.setStatus(r.status());
        if (r.queueIds() != null) {
            u.setAssignedQueues(resolveQueues(r.queueIds()));
        }
        if (disabling || roleChanged) {
            authService.revokeAllSessions(id);
        }
        audit.record("USER_UPDATED", "USER", id, Map.of("status", u.getStatus().name(), "role", u.getRole().name()));
        return toDto(u, assignedIds(u));
    }

    public void resetPassword(UUID id, ResetPasswordRequest r) {
        AppUser u = users.findById(id).orElseThrow(this::notFound);
        u.setPasswordHash(encoder.encode(r.newPassword()));
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        authService.revokeAllSessions(id);
        audit.record("PASSWORD_RESET", "USER", id, Map.of("username", u.getUsername()));
    }

    private Set<ServiceQueue> resolveQueues(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        List<ServiceQueue> found = queues.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new ApiException(ErrorCode.QUEUE_NOT_FOUND);
        }
        return new HashSet<>(found);
    }

    private Set<UUID> assignedIds(AppUser u) {
        return u.getRole() == Role.STAFF ? new HashSet<>(users.findAssignedQueueIds(u.getId())) : Set.of();
    }

    // ================================================================ tokens & audit

    @Transactional(readOnly = true)
    public PageDto<AdminTokenRow> listTokens(UUID queueId, TokenStatus status, LocalDate date, int page, int size) {
        Specification<QueueToken> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (queueId != null) {
                p.add(cb.equal(root.get("queue").get("id"), queueId));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (date != null) {
                p.add(cb.equal(root.get("serviceDate"), date));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<QueueToken> result = tokens.findAll(spec,
                PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt")));
        List<AdminTokenRow> rows = result.getContent().stream()
                .map(t -> new AdminTokenRow(t.getId(), t.getTokenNumber(), t.getStatus(), t.isPriority(),
                        t.getQueue().getId(), t.getQueue().getName(), t.getCustomerName(),
                        t.getSessionId() != null && !t.getSessionId().startsWith("u:"),
                        t.getServiceDate(), t.getCreatedAt(), t.getCalledAt(), t.getCompletedAt()))
                .toList();
        return new PageDto<>(rows, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageDto<AuditRow> listAudit(String action, String entityType, LocalDate from, LocalDate to,
                                       int page, int size) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (action != null && !action.isBlank()) {
                p.add(cb.equal(root.get("action"), action));
            }
            if (entityType != null && !entityType.isBlank()) {
                p.add(cb.equal(root.get("entityType"), entityType));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<AuditLog> result = auditLogs.findAll(spec,
                PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt")));
        List<AuditRow> rows = result.getContent().stream()
                .map(a -> new AuditRow(a.getId(), a.getActorLabel(), a.getAction(), a.getEntityType(),
                        a.getEntityId(), a.getIpAddress(), a.getMetadata(), a.getCreatedAt()))
                .toList();
        return new PageDto<>(rows, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public SettingsDto settings() {
        var s = props.security();
        return new SettingsDto(props.publicBaseUrl(), props.jwt().accessTokenTtlMinutes(),
                props.jwt().refreshTokenTtlDays(), s.loginMaxAttempts(), s.loginLockoutMinutes(),
                s.rateLimitPerMinute(), s.joinRateLimitPerMinute(), props.seed() != null && props.seed().enabled());
    }

    // ================================================================ mapping helpers

    private ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private QueueConfiguration requireConfig(UUID queueId) {
        return configs.findByQueueId(queueId).orElseThrow(() -> new ApiException(ErrorCode.QUEUE_NOT_FOUND));
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private static OrganizationDto toDto(Organization o) {
        return new OrganizationDto(o.getId(), o.getName(), o.getSlug(), o.getDescription(), o.isActive());
    }

    private static DepartmentDto toDto(Department d) {
        return new DepartmentDto(d.getId(), d.getBranch().getId(), d.getBranch().getName(), d.getName(), d.isActive());
    }

    private static ServiceDto toDto(ServiceOffering s) {
        return new ServiceDto(s.getId(), s.getDepartment().getId(), s.getDepartment().getName(),
                s.getDepartment().getBranch().getName(), s.getName(), s.getDescription(), s.isActive());
    }

    private static UserAdminDto toDto(AppUser u, Set<UUID> queueIds) {
        return new UserAdminDto(u.getId(), u.getEmail(), u.getUsername(), u.getFullName(), u.getRole(), u.getStatus(),
                u.getOrganization() == null ? null : u.getOrganization().getId(), u.getLastLoginAt(), queueIds,
                u.getCreatedAt());
    }

    private QueueAdminDto toAdminDto(ServiceQueue q, QueueConfiguration cfg) {
        var dept = q.getService().getDepartment();
        QueueConfigDto c = new QueueConfigDto(cfg.getTokenPrefix(), cfg.getPriorityPrefix(), cfg.getStartingNumber(),
                cfg.getNumberPadding(), cfg.getMaxQueueSize(), cfg.getPriorityMode(), cfg.getPriorityAfterN(),
                cfg.getAvgServiceMinutes(), cfg.getOpeningTime(), cfg.getClosingTime(), cfg.getBreakStart(),
                cfg.getBreakEnd(), cfg.getAutoExpireMinutes(), cfg.getNoShowTimeoutMinutes(),
                cfg.isAllowDuplicateActive(), cfg.isDirectServing(), cfg.getNearTurnThreshold());
        return new QueueAdminDto(q.getId(), q.getPublicRef(), q.getName(), q.getStatus(), q.isActive(),
                q.getService().getId(), q.getService().getName(), dept.getName(), dept.getBranch().getName(),
                dept.getBranch().getOrganization().getName(), c,
                tokens.countByQueueIdAndStatus(q.getId(), TokenStatus.WAITING), qr.joinUrl(q.getPublicRef()));
    }
}
