package com.collab.platform.common.core.exception;

/**
 * Business exception with a status code and message.
 * Caught by {@code GlobalExceptionHandler} to produce structured error responses.
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(Throwable cause) {
        super(cause);
        this.code = 500;
    }

    public int getCode() {
        return code;
    }
}
