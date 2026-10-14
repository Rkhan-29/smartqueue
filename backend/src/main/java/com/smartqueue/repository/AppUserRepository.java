package com.smartqueue.repository;

import com.smartqueue.entity.AppUser;
import com.smartqueue.entity.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<AppUser> {

    @Query("select u from AppUser u where lower(u.email) = lower(:login) or lower(u.username) = lower(:login)")
    Optional<AppUser> findByLogin(@Param("login") String login);

    @Query("select count(u) > 0 from AppUser u where lower(u.email) = lower(:email)")
    boolean emailExists(@Param("email") String email);

    @Query("select count(u) > 0 from AppUser u where lower(u.username) = lower(:username)")
    boolean usernameExists(@Param("username") String username);

    @Query("select count(u) > 0 from AppUser u join u.assignedQueues q "
            + "where u.id = :userId and q.id = :queueId and u.status = com.smartqueue.entity.UserStatus.ACTIVE")
    boolean isActiveAndAssigned(@Param("userId") UUID userId, @Param("queueId") UUID queueId);

    @Query("select count(u) > 0 from AppUser u "
            + "where u.id = :userId and u.status = com.smartqueue.entity.UserStatus.ACTIVE")
    boolean isActive(@Param("userId") UUID userId);

    @Query("select q.id from AppUser u join u.assignedQueues q where u.id = :userId")
    List<UUID> findAssignedQueueIds(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = "assignedQueues")
    Optional<AppUser> findWithAssignedQueuesById(UUID id);

    List<AppUser> findByRoleInOrderByFullNameAsc(Collection<Role> roles);

    long countByRole(Role role);
}
