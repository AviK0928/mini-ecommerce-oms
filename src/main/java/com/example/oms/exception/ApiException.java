package com.example.oms.exception;

import org.springframework.http.HttpStatus;

/**
 * A business or validation error that should be returned to the client
 * with a specific HTTP status and a readable message.
 * It is a RuntimeException, so throwing it inside a @Transactional method rolls the transaction back.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}