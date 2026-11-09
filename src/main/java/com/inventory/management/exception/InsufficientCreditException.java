
package com.inventory.management.exception;

public class InsufficientCreditException extends RuntimeException {

    public InsufficientCreditException(String message) {
        super(message);
    }
}
