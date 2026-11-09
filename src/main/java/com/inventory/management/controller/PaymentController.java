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

    @PostMapping("/sales-invoice/{invoiceId}")
    public ResponseEntity<PaymentResponse> recordCustomerPayment(
            @PathVariable Long invoiceId,
            @Valid @RequestBody PaymentRequest request) {

        PaymentResponse response =
                paymentService.recordCustomerPayment(invoiceId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/purchase-invoice/{invoiceId}")
    public ResponseEntity<PaymentResponse> recordSupplierPayment(
            @PathVariable Long invoiceId,
            @Valid @RequestBody PaymentRequest request) {

        PaymentResponse response =
                paymentService.recordSupplierPayment(invoiceId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getAllPayments() {

        return ResponseEntity.ok(
                paymentService.getAllPayments()
        );
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentById(
            @PathVariable Long paymentId) {

        return ResponseEntity.ok(
                paymentService.getPaymentById(paymentId)
        );
    }

    @GetMapping("/sales-invoice/{invoiceId}")
    public ResponseEntity<List<PaymentResponse>> getPaymentBySalesInvoice(
            @PathVariable Long invoiceId) {

        return ResponseEntity.ok(
                paymentService.getPaymentBySalesInvoice(invoiceId)
        );
    }

    @GetMapping("/purchase-invoice/{invoiceId}")
    public ResponseEntity<List<PaymentResponse>> getPaymentByPurchaseInvoice(
            @PathVariable Long invoiceId) {

        return ResponseEntity.ok(
                paymentService.getPaymentByPurchaseInvoice(invoiceId)
        );
    }
}