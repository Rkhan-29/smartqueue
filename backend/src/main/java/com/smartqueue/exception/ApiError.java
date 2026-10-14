package com.smartqueue.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/** Uniform error body returned for every failed request. Never contains internal details. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Instant timestamp, String path,
                       Map<String, String> fieldErrors) {

    public static ApiError of(ErrorCode code, String message, String path) {
        return new ApiError(code.name(), message, Instant.now(), path, null);
    }

    public static ApiError of(ErrorCode code, String path) {
        return of(code, code.defaultMessage(), path);
    }
}
