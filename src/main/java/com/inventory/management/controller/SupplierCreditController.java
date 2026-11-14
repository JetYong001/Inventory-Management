package com.inventory.management.controller;

import com.inventory.management.dto.request.SupplierCreditApplicationRequest;
import com.inventory.management.dto.request.SupplierCreditReversalRequest;
import com.inventory.management.dto.request.SupplierRefundRequest;

import com.inventory.management.dto.response.SupplierCreditApplicationResponse;
import com.inventory.management.dto.response.SupplierCreditBalanceResponse;
import com.inventory.management.dto.response.SupplierCreditReversalResponse;
import com.inventory.management.dto.response.SupplierRefundResponse;

import com.inventory.management.service.SupplierCreditService;
import com.inventory.management.service.SupplierRefundService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/supplier-credits")
@CrossOrigin(origins = "*")
public class SupplierCreditController {

    private final SupplierCreditService supplierCreditService;
    private final SupplierRefundService supplierRefundService;

    public SupplierCreditController(
            SupplierCreditService supplierCreditService,
            SupplierRefundService supplierRefundService
    ) {
        this.supplierCreditService = supplierCreditService;
        this.supplierRefundService = supplierRefundService;
    }

    @GetMapping("/{supplierId}")
    public ResponseEntity<SupplierCreditBalanceResponse> getSupplierCreditBalance(
            @PathVariable Long supplierId
    ) {
        return ResponseEntity.ok(
                supplierCreditService.getSupplierCreditBalance(supplierId)
        );
    }

    @PostMapping("/apply")
    public ResponseEntity<SupplierCreditApplicationResponse> applySupplierCredit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SupplierCreditApplicationRequest request
    ) {
        return ResponseEntity.ok(
                supplierCreditService.applySupplierCredit(
                        request,
                        idempotencyKey
                )
        );
    }

    @PostMapping("/reverse/{transactionId}")
    public ResponseEntity<SupplierCreditReversalResponse> reverseSupplierCreditApplication(
            @PathVariable Long transactionId,
            @Valid @RequestBody SupplierCreditReversalRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        supplierCreditService.reverseSupplierCreditApplication(
                                transactionId,
                                request
                        )
                );
    }

    @PostMapping("/refund")
    public ResponseEntity<SupplierRefundResponse> createRefund(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SupplierRefundRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        supplierRefundService.createRefund(
                                request,
                                idempotencyKey
                        )
                );
    }

    @GetMapping("/refunds/{supplierId}")
    public ResponseEntity<List<SupplierRefundResponse>> getRefunds(
            @PathVariable Long supplierId
    ) {
        return ResponseEntity.ok(
                supplierRefundService.getRefundsBySupplierId(supplierId)
        );
    }
}