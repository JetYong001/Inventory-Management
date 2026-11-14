package com.inventory.management.controller;

import com.inventory.management.dto.request.SalesReturnRequest;
import com.inventory.management.dto.response.SalesReturnResponse;
import com.inventory.management.service.SalesReturnService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales-returns")
@CrossOrigin(origins = "*")
public class SalesReturnController {

    private final SalesReturnService salesReturnService;

    public SalesReturnController(
            SalesReturnService salesReturnService
    ) {
        this.salesReturnService = salesReturnService;
    }

    // =========================
    // CREATE SALES RETURN
    // =========================

    @PostMapping
    public ResponseEntity<SalesReturnResponse> createSalesReturn(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SalesReturnRequest request
    ) {

        SalesReturnResponse response =
                salesReturnService.createSalesReturn(
                        request,
                        idempotencyKey
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // =========================
    // GET SALES RETURN BY ID
    // =========================

    @GetMapping("/{returnId}")
    public ResponseEntity<SalesReturnResponse> getSalesReturnById(
            @PathVariable Long returnId
    ) {

        return ResponseEntity.ok(
                salesReturnService.getSalesReturnById(returnId)
        );
    }

    // =========================
    // GET SALES RETURNS BY SALES ID
    // =========================

    @GetMapping("/sales/{salesId}")
    public ResponseEntity<List<SalesReturnResponse>> getSalesReturnsBySalesId(
            @PathVariable Long salesId
    ) {

        return ResponseEntity.ok(
                salesReturnService.getSalesReturnsBySalesId(salesId)
        );
    }
}