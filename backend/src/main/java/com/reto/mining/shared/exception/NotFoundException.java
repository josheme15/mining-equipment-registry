package com.reto.mining.shared.exception;

/** El recurso solicitado no existe → HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
