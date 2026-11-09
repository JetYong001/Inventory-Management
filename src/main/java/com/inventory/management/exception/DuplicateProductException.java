package com.inventory.management.exception;

public class DuplicateProductException extends RuntimeException {
    public DuplicateProductException(String message) {
        super(message);
    }
}
