package com.chrionline.security;

/**
 * Raised when a client-provided input fails security validation.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
