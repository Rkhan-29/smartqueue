package com.smartqueue.repository;

import com.smartqueue.entity.ServiceOffering;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {

    @EntityGraph(attributePaths = {"department", "department.branch"})
    List<ServiceOffering> findByDepartmentIdOrderByNameAsc(UUID departmentId);

    @EntityGraph(attributePaths = {"department", "department.branch"})
    List<ServiceOffering> findAllByOrderByNameAsc();
}
