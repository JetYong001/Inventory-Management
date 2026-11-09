
package com.inventory.management.service;

import com.inventory.management.dto.request.ProductRequest;
import com.inventory.management.dto.response.ProductResponse;
import com.inventory.management.entity.Category;
import com.inventory.management.entity.Product;
import com.inventory.management.exception.DuplicateProductException;
import com.inventory.management.exception.ProductNotFoundException;
import com.inventory.management.exception.CategoryNotFoundException;
import com.inventory.management.repository.CategoryRepository;
import com.inventory.management.repository.ProductRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id",
            "productCode",
            "productName",
            "quantity",
            "sellingPrice",
            "costPrice",
            "createdAt"
    );

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    // CREATE PRODUCT

    public ProductResponse createProduct(ProductRequest request) {

        if (productRepository.existsByProductCode(request.getProductCode())) {
            throw new DuplicateProductException(
                    "Product code already exists: " + request.getProductCode()
            );
        }

        Category category = findCategoryById(request.getCategoryId());

        Product product = new Product();

        product.setProductCode(request.getProductCode());
        product.setProductName(request.getProductName());
        product.setDescription(request.getDescription());
        product.setCostPrice(request.getCostPrice());
        product.setSellingPrice(request.getSellingPrice());
        product.setQuantity(0);
        product.setMinimumStock(request.getMinimumStock());
        product.setActive(true);
        product.setCategory(category);

        Product savedProduct = productRepository.save(product);

        return convertToResponse(savedProduct);
    }

    // GET ALL PRODUCTS WITH OPTIONAL ACTIVE FILTER

    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(
            Boolean active,
            int page,
            int size,
            String sortBy,
            String direction
    ) {

        Pageable pageable = createPageable(page, size, sortBy, direction);

        Page<Product> productPage;

        if (active == null) {
            productPage = productRepository.findAll(pageable);
        } else if (active) {
            productPage = productRepository.findAllByActiveTrue(pageable);
        } else {
            productPage = productRepository.findAllByActiveFalse(pageable);
        }

        return productPage.map(this::convertToResponse);
    }

    // GET PRODUCT BY ID

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {

        Product product = findProductById(id);

        return convertToResponse(product);
    }

    // SEARCH PRODUCTS WITH OPTIONAL ACTIVE FILTER

    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(
            String keyword,
            Boolean active,
            int page,
            int size,
            String sortBy,
            String direction
    ) {

        Pageable pageable = createPageable(page, size, sortBy, direction);

        String searchKeyword = keyword == null ? "" : keyword.trim();

        Page<Product> productPage = productRepository.searchProducts(
                searchKeyword,
                active,
                pageable
        );

        return productPage.map(this::convertToResponse);
    }

    // UPDATE PRODUCT
    public ProductResponse updateProduct(Long id, ProductRequest request) {

        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Active product not found with ID: " + id
                ));

        boolean duplicateCode = productRepository.existsByProductCode(
                request.getProductCode()
        );

        if (duplicateCode &&
                !product.getProductCode().equals(request.getProductCode())) {

            throw new DuplicateProductException(
                    "Product code already exists: " + request.getProductCode()
            );
        }

        Category category = findCategoryById(request.getCategoryId());

        product.setProductCode(request.getProductCode());
        product.setProductName(request.getProductName());
        product.setDescription(request.getDescription());
        product.setCostPrice(request.getCostPrice());
        product.setSellingPrice(request.getSellingPrice());
        product.setMinimumStock(request.getMinimumStock());
        product.setCategory(category);

        Product updatedProduct = productRepository.save(product);

        return convertToResponse(updatedProduct);
    }


    // SOFT DELETE PRODUCT

    public void deleteProduct(Long id) {

        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Active product not found with ID: " + id
                ));

        product.setActive(false);

        productRepository.save(product);
    }

    // REACTIVATE PRODUCT

    public ProductResponse reactivateProduct(Long id) {

        Product product = findProductById(id);

        if (Boolean.TRUE.equals(product.getActive())) {
            throw new IllegalArgumentException(
                    "Product is already active"
            );
        }

        product.setActive(true);

        Product savedProduct = productRepository.save(product);

        return convertToResponse(savedProduct);
    }

    // FIND PRODUCT BY ID

    private Product findProductById(Long id) {

        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product not found with ID: " + id
                ));
    }

    // FIND CATEGORY BY ID

    private Category findCategoryById(Long id) {

        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(
                        "Category not found with ID: " + id
                ));
    }

    // CREATE PAGEABLE

    private Pageable createPageable(
            int page,
            int size,
            String sortBy,
            String direction
    ) {

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page cannot be negative"
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100"
            );
        }

        if (sortBy == null || !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException(
                    "Invalid sort field: " + sortBy
            );
        }

        Sort.Direction sortDirection;

        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException(
                    "Direction must be either 'asc' or 'desc'"
            );
        }

        Sort sort = Sort.by(sortDirection, sortBy);

        return PageRequest.of(page, size, sort);
    }

    // CONVERT ENTITY TO RESPONSE DTO

    private ProductResponse convertToResponse(Product product) {

        ProductResponse response = new ProductResponse();

        response.setId(product.getId());
        response.setProductCode(product.getProductCode());
        response.setProductName(product.getProductName());
        response.setDescription(product.getDescription());
        response.setCostPrice(product.getCostPrice());
        response.setSellingPrice(product.getSellingPrice());
        response.setQuantity(product.getQuantity());
        response.setActive(product.getActive());
        response.setMinimumStock(product.getMinimumStock());
        response.setCreatedAt(product.getCreatedAt());

        if (product.getCategory() != null) {
            response.setCategoryId(product.getCategory().getId());
            response.setCategoryName(
                    product.getCategory().getCategoryName()
            );
        }

        return response;
    }
}
