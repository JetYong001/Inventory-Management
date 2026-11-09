package com.inventory.management.exception;

public class SalesInvoiceNotFoundException extends RuntimeException {

    public SalesInvoiceNotFoundException(String message) {
        super(message);
    }
}