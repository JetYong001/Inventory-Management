
package com.inventory.management.controller;

import com.inventory.management.dto.request.CustomerRequest;
import com.inventory.management.dto.response.CustomerResponse;
import com.inventory.management.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@CrossOrigin(origins = "*")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(
            @Valid @RequestBody CustomerRequest request) {

        CustomerResponse response = customerService.createCustomer(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CustomerResponse>> getAllCustomers() {

        List<CustomerResponse> customers = customerService.getAllCustomers();

        return ResponseEntity.ok(customers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomerById(
            @PathVariable Long id) {

        CustomerResponse response = customerService.getCustomerById(id);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody CustomerRequest request) {

        CustomerResponse response =
                customerService.updateCustomer(id, request);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateCustomer(@PathVariable Long id) {

        customerService.deactivateCustomer(id);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<CustomerResponse> activateCustomer(
            @PathVariable Long id) {

        CustomerResponse response = customerService.activateCustomer(id);

        return ResponseEntity.ok(response);
    }
}
