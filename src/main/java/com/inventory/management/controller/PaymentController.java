package com.inventory.management.controller;

import com.inventory.management.dto.request.PaymentRequest;
import com.inventory.management.dto.response.PaymentResponse;
import com.inventory.management.service.PaymentService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // ========================================
    // CUSTOMER PAYMENT
    // ========================================

    @PostMapping("/sales-invoice/{invoiceId}")
    public ResponseEntity<PaymentResponse> recordCustomerPayment(
            @PathVariable Long invoiceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request
    ) {
        PaymentResponse response =
                paymentService.recordCustomerPayment(
                        invoiceId,
                        request,
                        idempotencyKey
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // ========================================
    // SUPPLIER PAYMENT
    // ========================================

    @PostMapping("/purchase-invoice/{invoiceId}")
    public ResponseEntity<PaymentResponse> recordSupplierPayment(
            @PathVariable Long invoiceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request
    ) {
        PaymentResponse response =
                paymentService.recordSupplierPayment(
                        invoiceId,
                        request,
                        idempotencyKey
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // ========================================
    // GET ALL PAYMENTS
    // ========================================

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getAllPayments() {
        return ResponseEntity.ok(
                paymentService.getAllPayments()
        );
    }

    // ========================================
    // GET PAYMENT BY ID
    // ========================================

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentById(
            @PathVariable Long paymentId
    ) {
        return ResponseEntity.ok(
                paymentService.getPaymentById(paymentId)
        );
    }

    // ========================================
    // GET SALES INVOICE PAYMENTS
    // ========================================

    @GetMapping("/sales-invoice/{invoiceId}")
    public ResponseEntity<List<PaymentResponse>> getPaymentBySalesInvoice(
            @PathVariable Long invoiceId
    ) {
        return ResponseEntity.ok(
                paymentService.getPaymentBySalesInvoice(invoiceId)
        );
    }

    // ========================================
    // GET PURCHASE INVOICE PAYMENTS
    // ========================================

    @GetMapping("/purchase-invoice/{invoiceId}")
    public ResponseEntity<List<PaymentResponse>> getPaymentByPurchaseInvoice(
            @PathVariable Long invoiceId
    ) {
        return ResponseEntity.ok(
                paymentService.getPaymentByPurchaseInvoice(invoiceId)
        );
    }
}