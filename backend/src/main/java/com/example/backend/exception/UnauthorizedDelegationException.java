package com.example.backend.exception;

public class UnauthorizedDelegationException extends RuntimeException {
    public UnauthorizedDelegationException(String message) {
        super(message);
    }
}
