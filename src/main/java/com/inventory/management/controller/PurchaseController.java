package com.inventory.management.controller;

import com.inventory.management.dto.request.PurchaseRequest;
import com.inventory.management.dto.response.PurchaseResponse;
import com.inventory.management.service.PurchaseService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchases")
@CrossOrigin(origins = "*")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    public ResponseEntity<PurchaseResponse> createPurchase(
            @Valid @RequestBody PurchaseRequest request
    ) {
        PurchaseResponse response =
                purchaseService.createPurchase(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<PurchaseResponse>> getAllPurchases() {
        return ResponseEntity.ok(
                purchaseService.getAllPurchases()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseResponse> getPurchaseById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                purchaseService.getPurchaseById(id)
        );
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<PurchaseResponse> cancelPurchase(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                purchaseService.cancelPurchase(id)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<PurchaseResponse> updatePurchase(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseRequest request
    ) {
        return ResponseEntity.ok(
                purchaseService.updatePurchase(id, request)
        );
    }
}