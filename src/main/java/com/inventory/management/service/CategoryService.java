package com.inventory.management.service;

import com.inventory.management.dto.request.CategoryRequest;
import com.inventory.management.dto.response.CategoryDetailResponse;
import com.inventory.management.dto.response.CategoryProductResponse;
import com.inventory.management.dto.response.CategoryResponse;
import com.inventory.management.entity.Category;
import com.inventory.management.exception.CategoryNotFoundException;
import com.inventory.management.repository.CategoryRepository;
import com.inventory.management.repository.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public CategoryResponse createCategory(CategoryRequest request) {

        if (categoryRepository.existsByCategoryNameIgnoreCase(
                request.getCategoryName()
        )) {
            throw new IllegalArgumentException(
                    "Category already exists: " + request.getCategoryName()
            );
        }

        Category category = new Category();
        category.setCategoryName(request.getCategoryName().trim());
        category.setDescription(request.getDescription());

        return convertToResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {

        return categoryRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {

        return convertToResponse(findCategoryById(id));
    }

    public CategoryResponse updateCategory(
            Long id,
            CategoryRequest request
    ) {

        Category category = findCategoryById(id);

        String newName = request.getCategoryName().trim();

        boolean duplicate =
                categoryRepository.existsByCategoryNameIgnoreCase(newName);

        if (duplicate && !category.getCategoryName().equalsIgnoreCase(newName)) {
            throw new IllegalArgumentException(
                    "Category already exists: " + newName
            );
        }

        category.setCategoryName(newName);
        category.setDescription(request.getDescription());

        return convertToResponse(categoryRepository.save(category));
    }

    public void deleteCategory(Long id) {

        Category category = findCategoryById(id);

        if (productRepository.existsByCategory_Id(id)) {
            throw new IllegalArgumentException(
                    "Cannot delete this category because products are using it. "
                            + "Move those products to another category first."
            );
        }

        categoryRepository.delete(category);
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

                            CategoryProductResponse item =
                                    new CategoryProductResponse();

                            item.setId(product.getId());
                            item.setProductCode(product.getProductCode());
                            item.setProductName(product.getProductName());

                            return item;
                        })
                        .toList();

        response.setProducts(products);

        return response;
    }

    private Category findCategoryById(Long id) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "Valid category ID is required"
            );
        }

        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(
                        "Category not found with ID: " + id
                ));
    }

    private CategoryResponse convertToResponse(Category category) {

        CategoryResponse response = new CategoryResponse();

        response.setId(category.getId());
        response.setCategoryName(category.getCategoryName());
        response.setDescription(category.getDescription());
        response.setCreatedAt(category.getCreatedAt());

        return response;
    }
}