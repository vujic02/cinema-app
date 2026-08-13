package com.cinema.common.exception;

import org.springframework.http.HttpStatus;

/**
 * For input that is well-formed enough to deserialise and pass Bean Validation, but is still
 * wrong once the rest of the request is taken into account — an aisle gap past the end of its
 * row, a layout with duplicate row labels.
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
