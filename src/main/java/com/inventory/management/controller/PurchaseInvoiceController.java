package com.inventory.management.controller;

import com.inventory.management.dto.response.PurchaseInvoiceResponse;
import com.inventory.management.service.PurchaseInvoiceService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-invoices")
public class PurchaseInvoiceController {

    private final PurchaseInvoiceService purchaseInvoiceService;

    public PurchaseInvoiceController(
            PurchaseInvoiceService purchaseInvoiceService) {
        this.purchaseInvoiceService = purchaseInvoiceService;
    }

    // CREATE INVOICE FROM PURCHASE
    @PostMapping("/from-purchase/{purchaseId}")
    public ResponseEntity<PurchaseInvoiceResponse> createInvoiceFromPurchase(
            @PathVariable Long purchaseId) {

        PurchaseInvoiceResponse response =
                purchaseInvoiceService.createInvoiceFromPurchase(purchaseId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // GET ALL PURCHASE INVOICES
    @GetMapping
    public ResponseEntity<List<PurchaseInvoiceResponse>> getAllInvoices() {

        return ResponseEntity.ok(
                purchaseInvoiceService.getAllInvoices()
        );
    }

    // GET INVOICE BY ID
    @GetMapping("/{invoiceId}")
    public ResponseEntity<PurchaseInvoiceResponse> getInvoiceById(
            @PathVariable Long invoiceId) {

        return ResponseEntity.ok(
                purchaseInvoiceService.getInvoiceById(invoiceId)
        );
    }

    // GET INVOICE BY PURCHASE ID
    @GetMapping("/purchase/{purchaseId}")
    public ResponseEntity<PurchaseInvoiceResponse> getInvoiceByPurchaseId(
            @PathVariable Long purchaseId) {

        return ResponseEntity.ok(
                purchaseInvoiceService.getInvoiceByPurchaseId(purchaseId)
        );
    }
}