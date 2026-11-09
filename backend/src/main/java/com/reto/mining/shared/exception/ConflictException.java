package com.reto.mining.shared.exception;

/** El recurso choca con otro existente (p. ej. código duplicado) → HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
