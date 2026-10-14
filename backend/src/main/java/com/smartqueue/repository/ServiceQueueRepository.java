package com.smartqueue.repository;

import com.smartqueue.entity.ServiceQueue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceQueueRepository extends JpaRepository<ServiceQueue, UUID> {

    String GRAPH_PATHS_NOTE = "queue -> service -> department -> branch -> organization";

    /**
     * Row-level write lock on the queue. Every state-changing operation on a queue's tokens
     * (join, next, recall, skip, complete, ...) takes this lock first, serialising them per queue.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from ServiceQueue q where q.id = :id")
    Optional<ServiceQueue> lockById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"service", "service.department", "service.department.branch",
            "service.department.branch.organization"})
    Optional<ServiceQueue> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"service", "service.department", "service.department.branch",
            "service.department.branch.organization"})
    Optional<ServiceQueue> findWithDetailsByPublicRef(String publicRef);

    @EntityGraph(attributePaths = {"service", "service.department", "service.department.branch",
            "service.department.branch.organization"})
    List<ServiceQueue> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = {"service", "service.department", "service.department.branch",
            "service.department.branch.organization"})
    List<ServiceQueue> findByIdInOrderByNameAsc(Collection<UUID> ids);

    @EntityGraph(attributePaths = {"service", "service.department", "service.department.branch",
            "service.department.branch.organization"})
    List<ServiceQueue> findByActiveTrueOrderByNameAsc();

    @EntityGraph(attributePaths = {"service", "service.department", "service.department.branch",
            "service.department.branch.organization"})
    List<ServiceQueue> findByServiceIdAndActiveTrueOrderByNameAsc(UUID serviceId);

    @EntityGraph(attributePaths = {"service", "service.department", "service.department.branch",
            "service.department.branch.organization"})
    List<ServiceQueue> findByServiceDepartmentBranchIdAndActiveTrueOrderByNameAsc(UUID branchId);

    boolean existsByPublicRef(String publicRef);
}
