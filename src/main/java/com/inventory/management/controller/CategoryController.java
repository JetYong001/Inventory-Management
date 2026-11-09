package com.inventory.management.controller;

import com.inventory.management.dto.request.CategoryRequest;
import com.inventory.management.dto.response.CategoryDetailResponse;
import com.inventory.management.dto.response.CategoryResponse;
import com.inventory.management.service.CategoryService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    // Create Category
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CategoryRequest request
    ) {

        CategoryResponse response = categoryService.createCategory(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Get All Categories
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {

        List<CategoryResponse> categories =
                categoryService.getAllCategories();

        return ResponseEntity.ok(categories);
    }

    // Get Category by ID
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(
            @PathVariable Long id
    ) {

        CategoryResponse response = categoryService.getCategoryById(id);

        return ResponseEntity.ok(response);
    }

    // Update Category
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request
    ) {

        CategoryResponse response =
                categoryService.updateCategory(id, request);

        return ResponseEntity.ok(response);
    }

    // Delete Category
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long id
    ) {

        categoryService.deleteCategory(id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/products")
    public ResponseEntity<CategoryDetailResponse> getCategoryWithProducts(
            @PathVariable Long id
    ) {

        CategoryDetailResponse response =
                categoryService.getCategoryWithProducts(id);

        return ResponseEntity.ok(response);
    }
}