package com.smartqueue.config;

import com.smartqueue.entity.AppUser;
import com.smartqueue.entity.Branch;
import com.smartqueue.entity.Counter;
import com.smartqueue.entity.Department;
import com.smartqueue.entity.Organization;
import com.smartqueue.entity.PriorityMode;
import com.smartqueue.entity.QueueConfiguration;
import com.smartqueue.entity.Role;
import com.smartqueue.entity.ServiceOffering;
import com.smartqueue.entity.ServiceQueue;
import com.smartqueue.entity.UserStatus;
import com.smartqueue.repository.AppUserRepository;
import com.smartqueue.repository.BranchRepository;
import com.smartqueue.repository.CounterRepository;
import com.smartqueue.repository.DepartmentRepository;
import com.smartqueue.repository.OrganizationRepository;
import com.smartqueue.repository.QueueConfigurationRepository;
import com.smartqueue.repository.ServiceOfferingRepository;
import com.smartqueue.repository.ServiceQueueRepository;
import com.smartqueue.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * DEVELOPMENT ONLY. Creates a demo hospital with queues, counters and DEMO accounts.
 * Enabled by smartqueue.seed.enabled=true (set only in application-dev.yml). Idempotent.
 * The demo password comes from configuration (DEMO_PASSWORD) and is never hardcoded in this class.
 */
@Component
@ConditionalOnProperty(prefix = "smartqueue.seed", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements ApplicationRunner {

    private final AppProperties props;
    private final OrganizationRepository organizations;
    private final BranchRepository branches;
    private final DepartmentRepository departments;
    private final ServiceOfferingRepository services;
    private final ServiceQueueRepository queues;
    private final QueueConfigurationRepository configs;
    private final CounterRepository counters;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String password = props.seed().demoPassword();
        if (password == null || password.isBlank()) {
            log.warn("Demo seed skipped: set DEMO_PASSWORD to create demo accounts.");
            return;
        }
        if (organizations.existsBySlugIgnoreCase("smartcare-hospital")) {
            log.info("Demo data already present; skipping seed.");
            return;
        }

        Organization org = new Organization();
        org.setName("SmartCare Hospital");
        org.setSlug("smartcare-hospital");
        org.setDescription("DEMO organization - sample hospital for local development");
        organizations.save(org);

        Branch branch = new Branch();
        branch.setOrganization(org);
        branch.setName("Mumbai Central");
        branch.setCity("Mumbai");
        branch.setAddress("Demo address, Mumbai Central");
        branch.setTimeZone("Asia/Kolkata");
        branches.save(branch);

        ServiceQueue opd = seedQueue(branch, "Outpatient", "General Consultation", "General OPD",
                "A", PriorityMode.AFTER_N, 8);
        ServiceQueue billing = seedQueue(branch, "Accounts", "Billing", "Billing",
                "B", PriorityMode.DISABLED, 4);
        ServiceQueue pharmacy = seedQueue(branch, "Pharmacy", "Pharmacy", "Pharmacy",
                "M", PriorityMode.ALWAYS_FIRST, 3);

        for (ServiceQueue q : new ServiceQueue[]{opd, billing, pharmacy}) {
            for (int i = 1; i <= 3; i++) {
                Counter c = new Counter();
                c.setQueue(q);
                c.setName("Counter " + i);
                counters.save(c);
            }
        }

        createUser(org, "demo.admin", "demo.admin@smartqueue.demo", "Demo Admin", Role.ADMIN, password, Set.of());
        createUser(org, "demo.staff", "demo.staff@smartqueue.demo", "Demo Staff", Role.STAFF, password,
                Set.of(opd, billing, pharmacy));
        createUser(null, "demo.customer", "demo.customer@smartqueue.demo", "Demo Customer", Role.CUSTOMER,
                password, Set.of());

        log.warn("DEMO DATA CREATED. Demo accounts (development only): demo.admin, demo.staff, demo.customer. "
                + "Never enable the seed in production.");
    }

    private ServiceQueue seedQueue(Branch branch, String departmentName, String serviceName, String queueName,
                                   String prefix, PriorityMode mode, int avgMinutes) {
        Department d = new Department();
        d.setBranch(branch);
        d.setName(departmentName);
        departments.save(d);

        ServiceOffering s = new ServiceOffering();
        s.setDepartment(d);
        s.setName(serviceName);
        services.save(s);

        ServiceQueue q = new ServiceQueue();
        q.setService(s);
        q.setName(queueName);
        q.setPublicRef(TokenHasher.randomUrlSafe(9));
        queues.save(q);

        QueueConfiguration cfg = new QueueConfiguration();
        cfg.setQueue(q);
        cfg.setTokenPrefix(prefix);
        cfg.setPriorityPrefix("P" + prefix);
        cfg.setPriorityMode(mode);
        cfg.setAvgServiceMinutes(avgMinutes);
        configs.save(cfg);
        return q;
    }

    private void createUser(Organization org, String username, String email, String fullName, Role role,
                            String password, Set<ServiceQueue> assigned) {
        AppUser u = new AppUser();
        u.setOrganization(org);
        u.setUsername(username);
        u.setEmail(email);
        u.setFullName(fullName);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setAssignedQueues(new HashSet<>(assigned));
        users.save(u);
    }
}
