package com.opencomm.service;

import org.springframework.http.HttpStatus;

public class RoomException extends RuntimeException {
    private final HttpStatus status;

    public RoomException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
