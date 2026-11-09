package com.reto.mining.shared.exception;

/** Los datos son válidos en forma pero violan una regla de negocio → HTTP 422. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
