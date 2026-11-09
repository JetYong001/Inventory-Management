package com.inventory.management.exception;

public class InvalidSalesOperationException extends RuntimeException {

    public InvalidSalesOperationException(String message) {
        super(message);
    }
}