package com.inventory.management.exception;

public class SalesNotFoundException extends RuntimeException {

    public SalesNotFoundException(String message) {
        super(message);
    }
}