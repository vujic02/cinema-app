package com.cinema.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Always 401, never 403. Part 7's axios interceptor triggers its silent refresh on a 401, so
 * an authentication failure that returns 403 would leave the frontend unable to recover.
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }
}
