package com.smartqueue.repository;

import com.smartqueue.entity.Branch;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    @EntityGraph(attributePaths = "organization")
    List<Branch> findByOrganizationIdAndActiveTrueOrderByNameAsc(UUID organizationId);

    @EntityGraph(attributePaths = "organization")
    List<Branch> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "organization")
    List<Branch> findByOrganizationIdOrderByNameAsc(UUID organizationId);
}
