package com.aicit.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String msg) { super(msg); }
    public ResourceNotFoundException(String entity, Long id) {
        super(entity + " not found with id: " + id);
    }
}
