
package com.inventory.management.controller;

import com.inventory.management.dto.request.CustomerCreditApplicationRequest;
import com.inventory.management.dto.request.CustomerRefundRequest;
import com.inventory.management.dto.response.CustomerCreditApplicationResponse;
import com.inventory.management.dto.response.CustomerCreditBalanceResponse;
import com.inventory.management.dto.response.CustomerRefundResponse;
import com.inventory.management.service.CustomerCreditService;
import com.inventory.management.service.CustomerRefundService;

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
    public ResponseEntity<CustomerCreditBalanceResponse>
    getCustomerCreditBalance(@PathVariable Long customerId) {

        return ResponseEntity.ok(
                customerCreditService.getCustomerCreditBalance(customerId)
        );
    }

    @PostMapping("/apply")
    public ResponseEntity<CustomerCreditApplicationResponse>
    applyCustomerCredit(
            @RequestBody CustomerCreditApplicationRequest request
    ) {

        return ResponseEntity.ok(
                customerCreditService.applyCustomerCredit(request)
        );
    }

    @PostMapping("/refund")
    public ResponseEntity<CustomerRefundResponse> createRefund(
            @RequestBody CustomerRefundRequest request
    ) {

        return ResponseEntity.ok(
                customerRefundService.createRefund(request)
        );
    }

    @GetMapping("/refunds/{customerId}")
    public ResponseEntity<List<CustomerRefundResponse>>
    getCustomerRefunds(@PathVariable Long customerId) {

        return ResponseEntity.ok(
                customerRefundService.getCustomerRefunds(customerId)
        );
    }
}
