package com.cinema.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base for errors that map to a deliberate HTTP status and a stable machine-readable
 * code the frontend can branch on (e.g. SEAT_UNAVAILABLE vs HOLD_EXPIRED).
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
