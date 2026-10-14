package com.smartqueue;

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
import com.smartqueue.security.AuthenticatedUser;
import com.smartqueue.security.TokenHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Builds an isolated organization -> queue hierarchy per test so tests never share queue state. */
@Component
public class TestData {

    public record Fixture(ServiceQueue queue, AppUser admin, AppUser staff, Counter counter1, Counter counter2) {
    }

    @Autowired OrganizationRepository organizations;
    @Autowired BranchRepository branches;
    @Autowired DepartmentRepository departments;
    @Autowired ServiceOfferingRepository services;
    @Autowired ServiceQueueRepository queues;
    @Autowired QueueConfigurationRepository configs;
    @Autowired CounterRepository counters;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    public Fixture createQueue(Consumer<QueueConfiguration> configure) {
        String unique = UUID.randomUUID().toString().substring(0, 8);

        Organization org = new Organization();
        org.setName("Org " + unique);
        org.setSlug("org-" + unique);
        organizations.save(org);

        Branch branch = new Branch();
        branch.setOrganization(org);
        branch.setName("Branch " + unique);
        branch.setTimeZone("UTC");
        branches.save(branch);

        Department dept = new Department();
        dept.setBranch(branch);
        dept.setName("Dept " + unique);
        departments.save(dept);

        ServiceOffering service = new ServiceOffering();
        service.setDepartment(dept);
        service.setName("Service " + unique);
        services.save(service);

        ServiceQueue queue = new ServiceQueue();
        queue.setService(service);
        queue.setName("Queue " + unique);
        queue.setPublicRef(TokenHasher.randomUrlSafe(9));
        queues.save(queue);

        QueueConfiguration cfg = new QueueConfiguration();
        cfg.setQueue(queue);
        cfg.setPriorityMode(PriorityMode.DISABLED);
        cfg.setMaxQueueSize(1000);
        if (configure != null) {
            configure.accept(cfg);
        }
        configs.save(cfg);

        Counter c1 = counter(queue, "Counter 1");
        Counter c2 = counter(queue, "Counter 2");

        AppUser admin = user("admin-" + unique, Role.ADMIN, Set.of());
        AppUser staff = user("staff-" + unique, Role.STAFF, Set.of(queue));
        return new Fixture(queue, admin, staff, c1, c2);
    }

    private Counter counter(ServiceQueue q, String name) {
        Counter c = new Counter();
        c.setQueue(q);
        c.setName(name);
        return counters.save(c);
    }

    public AppUser user(String username, Role role, Set<ServiceQueue> assigned) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setEmail(username + "@test.local");
        u.setFullName(username);
        u.setPasswordHash(encoder.encode("Passw0rd!Passw0rd"));
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setAssignedQueues(new HashSet<>(assigned));
        return users.save(u);
    }

    /** Authenticates the current thread as the given user (SecurityContext is thread-local). */
    public static void actAs(AppUser user) {
        var principal = new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
