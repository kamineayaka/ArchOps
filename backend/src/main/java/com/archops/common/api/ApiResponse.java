package com.archops.common.api;

/**
 * Unified HTTP envelope. All four fields are always serialized so clients and
 * acceptance tests can assert a stable shape (including {@code data: null} on errors).
 */
public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "ok", data);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
