package com.smartqueue.exception;

/** Business exception carrying a stable, client-facing error code. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code) {
        super(code.defaultMessage(), null, false, false);
        this.code = code;
    }

    public ApiException(ErrorCode code, String message) {
        super(message, null, false, false);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
