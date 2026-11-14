package com.inventory.management.controller;

import com.inventory.management.dto.request.CustomerCreditApplicationRequest;
import com.inventory.management.dto.request.CustomerCreditReversalRequest;
import com.inventory.management.dto.request.CustomerRefundRequest;

import com.inventory.management.dto.response.CustomerCreditApplicationResponse;
import com.inventory.management.dto.response.CustomerCreditBalanceResponse;
import com.inventory.management.dto.response.CustomerCreditReversalResponse;
import com.inventory.management.dto.response.CustomerRefundResponse;

import com.inventory.management.service.CustomerCreditService;
import com.inventory.management.service.CustomerRefundService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer-credits")
@CrossOrigin(origins = "*")
public class CustomerCreditController {

    private final CustomerCreditService customerCreditService;
    private final CustomerRefundService customerRefundService;

    public CustomerCreditController(
            CustomerCreditService customerCreditService,
            CustomerRefundService customerRefundService
    ) {
        this.customerCreditService = customerCreditService;
        this.customerRefundService = customerRefundService;
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerCreditBalanceResponse> getCustomerCreditBalance(
            @PathVariable Long customerId
    ) {
        return ResponseEntity.ok(
                customerCreditService.getCustomerCreditBalance(customerId)
        );
    }

    @PostMapping("/apply")
    public ResponseEntity<CustomerCreditApplicationResponse> applyCustomerCredit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CustomerCreditApplicationRequest request
    ) {
        return ResponseEntity.ok(
                customerCreditService.applyCustomerCredit(
                        request,
                        idempotencyKey
                )
        );
    }

    @PostMapping("/reverse/{transactionId}")
    public ResponseEntity<CustomerCreditReversalResponse> reverseCustomerCreditApplication(
            @PathVariable Long transactionId,
            @Valid @RequestBody CustomerCreditReversalRequest request
    ) {
        CustomerCreditReversalResponse response =
                customerCreditService.reverseCustomerCreditApplication(
                        transactionId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/refund")
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

    @GetMapping("/refunds/{customerId}")
    public ResponseEntity<List<CustomerRefundResponse>> getCustomerRefunds(
            @PathVariable Long customerId
    ) {
        return ResponseEntity.ok(
                customerRefundService.getCustomerRefunds(customerId)
        );
    }
}