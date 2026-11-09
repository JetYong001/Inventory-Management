
package com.inventory.management.controller;

import com.inventory.management.dto.request.ProductRequest;
import com.inventory.management.dto.response.ProductResponse;
import com.inventory.management.service.ProductService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // CREATE PRODUCT

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductRequest request
    ) {

        ProductResponse response = productService.createProduct(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET ALL PRODUCTS WITH OPTIONAL ACTIVE FILTER

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "productName") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {

        Page<ProductResponse> products = productService.getAllProducts(
                active,
                page,
                size,
                sortBy,
                direction
        );

        return ResponseEntity.ok(products);
    }

    // GET PRODUCT BY ID

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(
            @PathVariable Long id
    ) {

        ProductResponse response = productService.getProductById(id);

        return ResponseEntity.ok(response);
    }

    // SEARCH PRODUCTS WITH OPTIONAL ACTIVE FILTER

    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam String keyword,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "productName") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {

        Page<ProductResponse> products = productService.searchProducts(
                keyword,
                active,
                page,
                size,
                sortBy,
                direction
        );

        return ResponseEntity.ok(products);
    }

    // UPDATE PRODUCT

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request
    ) {

        ProductResponse response = productService.updateProduct(id, request);

        return ResponseEntity.ok(response);
    }

    // REACTIVATE PRODUCT

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<ProductResponse> reactivateProduct(
            @PathVariable Long id
    ) {

        ProductResponse response = productService.reactivateProduct(id);

        return ResponseEntity.ok(response);
    }

    // DELETE PRODUCT (SOFT DELETE)

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long id
    ) {

        productService.deleteProduct(id);

        return ResponseEntity.noContent().build();
    }
}
