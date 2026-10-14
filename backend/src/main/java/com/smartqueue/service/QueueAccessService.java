package com.smartqueue.service;

import com.smartqueue.entity.Role;
import com.smartqueue.exception.ApiException;
import com.smartqueue.exception.ErrorCode;
import com.smartqueue.repository.AppUserRepository;
import com.smartqueue.security.AuthenticatedUser;
import com.smartqueue.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Object-level authorisation for queue operations. Role checks on URLs are not enough: a staff member
 * may only operate queues they are assigned to. The account status is re-checked against the database
 * so that disabling a user takes effect immediately, not when their access token expires.
 */
@Service
@RequiredArgsConstructor
public class QueueAccessService {

    private final AppUserRepository users;

    public AuthenticatedUser requireStaffAccess(UUID queueId) {
        AuthenticatedUser user = CurrentUser.require();
        boolean allowed = switch (user.role()) {
            case ADMIN -> users.isActive(user.id());
            case STAFF -> users.isActiveAndAssigned(user.id(), queueId);
            case CUSTOMER -> false;
        };
        if (!allowed) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    public boolean isAdmin() {
        return CurrentUser.get().map(u -> u.role() == Role.ADMIN).orElse(false);
    }
}
