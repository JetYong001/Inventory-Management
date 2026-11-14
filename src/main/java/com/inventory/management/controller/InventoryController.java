
package com.inventory.management.controller;

import com.inventory.management.dto.request.StockAdjustmentRequest;
import com.inventory.management.dto.request.OpeningStockRequest;

import com.inventory.management.dto.response.InventorySummaryResponse;
import com.inventory.management.dto.response.StockBalanceResponse;
import com.inventory.management.dto.response.StockMovementResponse;

import com.inventory.management.entity.Product;

import com.inventory.management.service.InventoryService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@CrossOrigin(origins = "*")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    // OPENING STOCK

    @PostMapping("/opening-stock")
    public ResponseEntity<StockMovementResponse> initializeStock(
            @Valid @RequestBody OpeningStockRequest request) {

        return ResponseEntity.ok(
                inventoryService.initializeStock(
                        request.getProductId(),
                        request.getQuantity(),
                        request.getRemarks()
                )
        );
    }

    // STOCK ADJUSTMENT

    @PostMapping("/adjust")
    public ResponseEntity<StockMovementResponse> adjustStock(
            @Valid @RequestBody StockAdjustmentRequest request) {

        return ResponseEntity.ok(
                inventoryService.adjustStock(request));
    }

    // STOCK HISTORY

    @GetMapping("/history/{productId}")
    public ResponseEntity<List<StockMovementResponse>> getStockHistory(
            @PathVariable Long productId) {

        return ResponseEntity.ok(
                inventoryService.getProductStockHistory(productId));
    }

    // LOW STOCK

    @GetMapping("/low-stock")
    public ResponseEntity<List<StockBalanceResponse>> getLowStockProducts() {

        return ResponseEntity.ok(
                inventoryService.getLowStockProducts());
    }

    // ALL STOCK BALANCE

    @GetMapping("/stock")
    public ResponseEntity<List<StockBalanceResponse>> getAllStockBalances() {

        return ResponseEntity.ok(
                inventoryService.getAllStockBalances());
    }

    // STOCK BALANCE BY PRODUCT ID

    @GetMapping("/stock/{productId}")
    public ResponseEntity<StockBalanceResponse> getStockBalanceByProductId(
            @PathVariable Long productId) {

        return ResponseEntity.ok(
                inventoryService.getStockBalanceByProductId(productId));
    }

    // INVENTORY SUMMARY

    @GetMapping("/summary")
    public ResponseEntity<InventorySummaryResponse> getInventorySummary() {

        return ResponseEntity.ok(
                inventoryService.getInventorySummary());
    }
}
