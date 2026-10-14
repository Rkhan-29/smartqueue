package com.smartqueue.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Token lifecycle. All transitions are validated through {@link #canTransitionTo(TokenStatus)}.
 *
 * WAITING   -> CALLED, SERVING, CANCELLED, SKIPPED, NO_SHOW
 * CALLED    -> SERVING, COMPLETED, SKIPPED, NO_SHOW, CANCELLED, WAITING (return to line)
 * SERVING   -> COMPLETED, CANCELLED
 * SKIPPED   -> WAITING (re-queue by staff)
 * COMPLETED, CANCELLED, NO_SHOW -> terminal
 */
public enum TokenStatus {
    WAITING, CALLED, SERVING, COMPLETED, SKIPPED, CANCELLED, NO_SHOW;

    private static final Set<TokenStatus> ACTIVE = EnumSet.of(WAITING, CALLED, SERVING);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == NO_SHOW;
    }

    public boolean canTransitionTo(TokenStatus next) {
        return switch (this) {
            case WAITING -> next == CALLED || next == SERVING || next == CANCELLED
                    || next == SKIPPED || next == NO_SHOW;
            case CALLED -> next == SERVING || next == COMPLETED || next == SKIPPED
                    || next == NO_SHOW || next == CANCELLED || next == WAITING;
            case SERVING -> next == COMPLETED || next == CANCELLED;
            case SKIPPED -> next == WAITING || next == CANCELLED;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };
    }

    public static Set<TokenStatus> activeStatuses() {
        return EnumSet.copyOf(ACTIVE);
    }
}
