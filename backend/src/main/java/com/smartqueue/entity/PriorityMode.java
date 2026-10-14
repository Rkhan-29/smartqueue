package com.smartqueue.entity;

public enum PriorityMode {
    /** Priority tokens are not issued. */
    DISABLED,
    /** Priority tokens are always served before normal tokens. */
    ALWAYS_FIRST,
    /** One priority token is served after every N normal tokens. */
    AFTER_N
}
