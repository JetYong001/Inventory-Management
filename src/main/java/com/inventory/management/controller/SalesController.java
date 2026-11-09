
package com.inventory.management.controller;

import com.inventory.management.dto.request.SalesRequest;
import com.inventory.management.dto.response.SalesResponse;
import com.inventory.management.service.SalesService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales")
@CrossOrigin(origins = "*")
public class SalesController {

    private final SalesService salesService;

    public SalesController(SalesService salesService) {
        this.salesService = salesService;
    }

    @PostMapping
    public ResponseEntity<SalesResponse> createSales(
            @Valid @RequestBody SalesRequest request) {

        SalesResponse response = salesService.createSales(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SalesResponse>> getAllSales() {

        return ResponseEntity.ok(salesService.getAllSales());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SalesResponse> getSalesById(
            @PathVariable Long id) {

        return ResponseEntity.ok(salesService.getSalesById(id));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<SalesResponse> completeSales(
            @PathVariable Long id) {

        return ResponseEntity.ok(salesService.completeSales(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<SalesResponse> cancelSales(
            @PathVariable Long id) {

        return ResponseEntity.ok(salesService.cancelSales(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SalesResponse> updateSales(
            @PathVariable Long id,
            @Valid @RequestBody SalesRequest request) {

        return ResponseEntity.ok(
                salesService.updateSales(id, request)
        );
    }
}
