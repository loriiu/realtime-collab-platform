package com.collab.platform.common.core.result;

import java.io.Serializable;

/**
 * Unified API response wrapper.
 *
 * @param <T> the type of data payload
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** Return success with data. */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), "success", data);
    }

    /** Return success without data. */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), "success", null);
    }

    /** Return failure with custom code and message. */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    // --- getters / setters ---

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
