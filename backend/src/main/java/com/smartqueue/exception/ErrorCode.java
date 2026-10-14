package com.smartqueue.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // Queue / token domain
    QUEUE_NOT_FOUND(HttpStatus.NOT_FOUND, "Queue not found."),
    QUEUE_FULL(HttpStatus.CONFLICT, "This queue is full. Please try again later."),
    QUEUE_PAUSED(HttpStatus.CONFLICT, "This queue is currently paused."),
    QUEUE_CLOSED(HttpStatus.CONFLICT, "This queue is currently closed."),
    OUTSIDE_WORKING_HOURS(HttpStatus.CONFLICT, "This queue is outside its working hours."),
    QUEUE_ON_BREAK(HttpStatus.CONFLICT, "This queue is on a break right now. Please try again shortly."),
    COUNTER_BUSY(HttpStatus.CONFLICT, "This counter is still serving a token. Complete it first."),
    TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "Token not found."),
    INVALID_TOKEN_STATE(HttpStatus.CONFLICT, "This action is not allowed for the token's current state."),
    DUPLICATE_ACTIVE_TOKEN(HttpStatus.CONFLICT, "You already have an active token in this queue."),
    NO_WAITING_TOKEN(HttpStatus.CONFLICT, "There are no waiting tokens in this queue."),
    PRIORITY_DISABLED(HttpStatus.CONFLICT, "Priority tokens are not enabled for this queue."),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Too many failed attempts. Please try again later."),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "This account is disabled."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Session expired. Please sign in again."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please slow down."),

    // Generic
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "A resource with these details already exists."),
    CONFLICT(HttpStatus.CONFLICT, "The request conflicts with the current state. Please retry."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
