package com.inventory.management.controller;

import com.inventory.management.dto.response.SalesInvoiceResponse;
import com.inventory.management.service.SalesInvoiceService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales-invoices")
@CrossOrigin(origins = "*")
public class SalesInvoiceController {

    private final SalesInvoiceService salesInvoiceService;

    public SalesInvoiceController(
            SalesInvoiceService salesInvoiceService) {
        this.salesInvoiceService = salesInvoiceService;
    }

    // Generate invoice from completed sales
    @PostMapping("/from-sales/{salesId}")
    public ResponseEntity<SalesInvoiceResponse> createInvoiceFromSales(
            @PathVariable Long salesId) {

        SalesInvoiceResponse response =
                salesInvoiceService.createInvoiceFromSales(salesId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // Get all invoices
    @GetMapping
    public ResponseEntity<List<SalesInvoiceResponse>> getAllInvoices() {

        return ResponseEntity.ok(
                salesInvoiceService.getAllInvoices()
        );
    }

    // Get invoice by ID
    @GetMapping("/{invoiceId}")
    public ResponseEntity<SalesInvoiceResponse> getInvoiceById(
            @PathVariable Long invoiceId) {

        return ResponseEntity.ok(
                salesInvoiceService.getInvoiceById(invoiceId)
        );
    }

    // Get invoice by sales ID
    @GetMapping("/sales/{salesId}")
    public ResponseEntity<SalesInvoiceResponse> getInvoiceBySalesId(
            @PathVariable Long salesId) {

        return ResponseEntity.ok(
                salesInvoiceService.getInvoiceBySalesId(salesId)
        );
    }
}