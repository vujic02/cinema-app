package com.cinema.common.exception;

import java.time.Instant;
import java.util.Map;

/**
 * The single error shape every failed request returns.
 *
 * @param code           stable machine-readable code, e.g. NOT_FOUND, VALIDATION_FAILED
 * @param message        human-readable summary
 * @param path           request path that failed
 * @param fieldErrors    field -> message, populated only for validation failures
 * @param timestamp      when the error was produced
 */
public record ApiError(
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors,
        Instant timestamp
) {
    public static ApiError of(String code, String message, String path) {
        return new ApiError(code, message, path, null, Instant.now());
    }

    public static ApiError validation(String message, String path, Map<String, String> fieldErrors) {
        return new ApiError("VALIDATION_FAILED", message, path, fieldErrors, Instant.now());
    }
}
