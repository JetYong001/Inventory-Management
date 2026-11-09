
package com.inventory.management.controller;

import com.inventory.management.dto.request.SupplierRequest;
import com.inventory.management.dto.response.SupplierResponse;
import com.inventory.management.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@CrossOrigin(origins = "*")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @PostMapping
    public ResponseEntity<SupplierResponse> createSupplier(
            @Valid @RequestBody SupplierRequest request) {

        SupplierResponse response = supplierService.createSupplier(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SupplierResponse>> getAllSuppliers() {

        List<SupplierResponse> suppliers = supplierService.getAllSuppliers();

        return ResponseEntity.ok(suppliers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupplierResponse> getSupplierById(
            @PathVariable Long id) {

        SupplierResponse response = supplierService.getSupplierById(id);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SupplierResponse> updateSupplier(
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequest request) {

        SupplierResponse response = supplierService.updateSupplier(id, request);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSupplier(@PathVariable Long id) {

        supplierService.deleteSupplier(id);

        return ResponseEntity.noContent().build();
    }
}
