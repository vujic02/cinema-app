package com.cinema.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The loser of a seat race gets this — a 409 with a specific code, not a generic 500,
 * so the UI can revert the optimistic selection and say why.
 */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
