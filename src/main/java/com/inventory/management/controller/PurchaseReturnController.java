
package com.inventory.management.controller;

import com.inventory.management.dto.request.PurchaseReturnRequest;
import com.inventory.management.dto.response.PurchaseReturnResponse;
import com.inventory.management.service.PurchaseReturnService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-returns")
@CrossOrigin(origins = "*")
public class PurchaseReturnController {

    private final PurchaseReturnService purchaseReturnService;

    public PurchaseReturnController(
            PurchaseReturnService purchaseReturnService) {
        this.purchaseReturnService = purchaseReturnService;
    }

    // CREATE PURCHASE RETURN
    @PostMapping
    public ResponseEntity<PurchaseReturnResponse> createPurchaseReturn(
            @Valid @RequestBody PurchaseReturnRequest request) {

        PurchaseReturnResponse response =
                purchaseReturnService.createPurchaseReturn(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // GET PURCHASE RETURN BY ID
    @GetMapping("/{returnId}")
    public ResponseEntity<PurchaseReturnResponse> getPurchaseReturnById(
            @PathVariable Long returnId) {

        return ResponseEntity.ok(
                purchaseReturnService.getPurchaseReturnById(returnId)
        );
    }

    // GET PURCHASE RETURNS BY PURCHASE ID
    @GetMapping("/purchase/{purchaseId}")
    public ResponseEntity<List<PurchaseReturnResponse>>
    getPurchaseReturnsByPurchaseId(
            @PathVariable Long purchaseId) {

        return ResponseEntity.ok(
                purchaseReturnService.getPurchaseReturnsByPurchaseId(
                        purchaseId)
        );
    }
}
