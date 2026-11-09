package com.inventory.management.service;

import com.inventory.management.dto.request.CategoryRequest;
import com.inventory.management.dto.response.CategoryResponse;
import com.inventory.management.entity.Category;
import com.inventory.management.exception.CategoryNotFoundException;
import com.inventory.management.repository.CategoryRepository;
import com.inventory.management.dto.response.CategoryDetailResponse;
import com.inventory.management.dto.response.CategoryProductResponse;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // Create Category
    public CategoryResponse createCategory(CategoryRequest request) {

        if (categoryRepository.existsByCategoryNameIgnoreCase(
                request.getCategoryName()
        )) {
            throw new IllegalArgumentException(
                    "Category already exists: " + request.getCategoryName()
            );
        }

        Category category = new Category();

        category.setCategoryName(request.getCategoryName());
        category.setDescription(request.getDescription());

        Category savedCategory = categoryRepository.save(category);

        return convertToResponse(savedCategory);
    }

    // Get All Categories
    public List<CategoryResponse> getAllCategories() {

        return categoryRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // Get Category by ID
    public CategoryResponse getCategoryById(Long id) {

        Category category = findCategoryById(id);

        return convertToResponse(category);
    }

    // Update Category
    public CategoryResponse updateCategory(
            Long id,
            CategoryRequest request
    ) {

        Category category = findCategoryById(id);

        boolean duplicate = categoryRepository.existsByCategoryNameIgnoreCase(
                request.getCategoryName()
        );

        if (duplicate && !category.getCategoryName().equalsIgnoreCase(
                request.getCategoryName()
        )) {
            throw new IllegalArgumentException(
                    "Category already exists: " + request.getCategoryName()
            );
        }

        category.setCategoryName(request.getCategoryName());
        category.setDescription(request.getDescription());

        Category updatedCategory = categoryRepository.save(category);

        return convertToResponse(updatedCategory);
    }

    // Delete Category
    public void deleteCategory(Long id) {

        Category category = findCategoryById(id);

        categoryRepository.delete(category);
    }

    // Find Category
    private Category findCategoryById(Long id) {

        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(
                        "Category not found with ID: " + id
                ));
    }

    // Convert Entity to Response DTO
    private CategoryResponse convertToResponse(Category category) {

        CategoryResponse response = new CategoryResponse();

        response.setId(category.getId());
        response.setCategoryName(category.getCategoryName());
        response.setDescription(category.getDescription());
        response.setCreatedAt(category.getCreatedAt());

        return response;
    }

    @Transactional(readOnly = true)
    public CategoryDetailResponse getCategoryWithProducts(Long id) {

        Category category = categoryRepository.findWithProductsById(id)
                .orElseThrow(() -> new CategoryNotFoundException(
                        "Category not found with ID: " + id
                ));

        CategoryDetailResponse response = new CategoryDetailResponse();

        response.setId(category.getId());
        response.setCategoryName(category.getCategoryName());
        response.setDescription(category.getDescription());

        List<CategoryProductResponse> products =
                category.getProducts()
                        .stream()
                        .map(product -> {

                            CategoryProductResponse productResponse =
                                    new CategoryProductResponse();

                            productResponse.setId(product.getId());
                            productResponse.setProductCode(
                                    product.getProductCode()
                            );
                            productResponse.setProductName(
                                    product.getProductName()
                            );

                            return productResponse;
                        })
                        .toList();

        response.setProducts(products);

        return response;
    }

}