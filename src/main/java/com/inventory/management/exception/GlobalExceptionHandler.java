package com.inventory.management.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ProductNotFoundException exception
    ) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
    }

    @ExceptionHandler(DuplicateProductException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(
            DuplicateProductException exception
    ) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            IllegalArgumentException exception
    ) {

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException exception
    ) {

        Map<String, String> validationErrors = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(error ->
                validationErrors.put(
                        error.getField(),
                        error.getDefaultMessage()
                )
        );

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("messages", validationErrors);

        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status,
            String message
    ) {

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);

        return ResponseEntity.status(status).body(body);
    }
    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCategoryNotFound(
            CategoryNotFoundException exception
    ) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
    }

    @ExceptionHandler(SupplierNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSupplierNotFound(
            SupplierNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateSupplierException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateSupplier(
            DuplicateSupplierException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }
    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCustomerNotFound(
            CustomerNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateCustomerException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateCustomer(
            DuplicateCustomerException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }
    @ExceptionHandler(PurchaseNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePurchaseNotFound(
            PurchaseNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidPurchaseOperationException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPurchaseOperation(
            InvalidPurchaseOperationException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(SalesNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSalesNotFound(
            SalesNotFoundException ex) {

        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidSalesOperationException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidSalesOperation(
            InvalidSalesOperationException ex) {

        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }


    @ExceptionHandler(SalesInvoiceNotFoundException.class)
    public ResponseEntity<?> handleSalesInvoiceNotFound(
            SalesInvoiceNotFoundException ex) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "status", 404,
                        "error", "Not Found",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(InsufficientCreditException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientCredit(
            InsufficientCreditException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "timestamp", LocalDateTime.now(),
                        "status", 400,
                        "error", "Insufficient Credit",
                        "message", exception.getMessage()
                ));
    }
}

