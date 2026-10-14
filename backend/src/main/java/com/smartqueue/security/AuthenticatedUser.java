package com.smartqueue.security;

import com.smartqueue.entity.Role;

import java.util.UUID;

/** Principal placed in the SecurityContext after a valid JWT is presented. */
public record AuthenticatedUser(UUID id, String username, Role role) {
}
