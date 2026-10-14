package com.smartqueue.repository;

import com.smartqueue.entity.Department;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    @EntityGraph(attributePaths = {"branch", "branch.organization"})
    List<Department> findByBranchIdOrderByNameAsc(UUID branchId);

    @EntityGraph(attributePaths = {"branch", "branch.organization"})
    List<Department> findAllByOrderByNameAsc();
}
