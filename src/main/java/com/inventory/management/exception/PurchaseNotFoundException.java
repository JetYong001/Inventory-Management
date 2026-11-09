
package com.inventory.management.exception;

public class PurchaseNotFoundException extends RuntimeException {

    public PurchaseNotFoundException(String message) {
        super(message);
    }
}
