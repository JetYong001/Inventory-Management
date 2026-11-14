package com.inventory.management.controller;

import com.inventory.management.dto.request.CustomerRefundRequest;
import com.inventory.management.dto.response.CustomerRefundResponse;
import com.inventory.management.service.CustomerRefundService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer-refunds")
@CrossOrigin(origins = "*")
public class CustomerRefundController {

    private final CustomerRefundService customerRefundService;

    public CustomerRefundController(
            CustomerRefundService customerRefundService
    ) {
        this.customerRefundService = customerRefundService;
    }

    @PostMapping
    public ResponseEntity<CustomerRefundResponse> createRefund(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CustomerRefundRequest request
    ) {
        CustomerRefundResponse response =
                customerRefundService.createRefund(
                        request,
                        idempotencyKey
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<CustomerRefundResponse>> getCustomerRefunds(
            @PathVariable Long customerId
    ) {
        List<CustomerRefundResponse> refunds =
                customerRefundService.getCustomerRefunds(customerId);

        return ResponseEntity.ok(refunds);
    }
}