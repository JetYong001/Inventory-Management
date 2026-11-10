package com.inventory.management.service;

import com.inventory.management.dto.request.ProductRequest;
import com.inventory.management.dto.response.ProductResponse;
import com.inventory.management.entity.Category;
import com.inventory.management.entity.Product;
import com.inventory.management.exception.ProductNotFoundException;
import com.inventory.management.exception.CategoryNotFoundException;
import com.inventory.management.repository.CategoryRepository;
import com.inventory.management.repository.ProductRepository;
import com.inventory.management.util.BusinessCodeGenerator;

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
            "id", "productCode", "productName", "quantity",
            "sellingPrice", "costPrice", "createdAt"
    );

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public ProductResponse createProduct(ProductRequest request) {

        Category category = findCategoryById(request.getCategoryId());

        Product product = new Product();

        product.setProductCode(BusinessCodeGenerator.temporary());
        product.setProductName(request.getProductName());
        product.setDescription(request.getDescription());
        product.setCostPrice(request.getCostPrice());
        product.setSellingPrice(request.getSellingPrice());
        product.setQuantity(0);
        product.setMinimumStock(request.getMinimumStock());
        product.setActive(true);
        product.setCategory(category);

        Product saved = productRepository.saveAndFlush(product);

        saved.setProductCode(
                BusinessCodeGenerator.format("P", saved.getId())
        );

        return convertToResponse(productRepository.save(saved));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(
            Boolean active,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        Pageable pageable = createPageable(
                page, size, sortBy, direction
        );

        Page<Product> result;

        if (active == null) {
            result = productRepository.findAll(pageable);
        } else if (active) {
            result = productRepository.findAllByActiveTrue(pageable);
        } else {
            result = productRepository.findAllByActiveFalse(pageable);
        }

        return result.map(this::convertToResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        return convertToResponse(findProductById(id));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(
            String keyword,
            Boolean active,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        Pageable pageable = createPageable(
                page, size, sortBy, direction
        );

        String searchKeyword = keyword == null ? "" : keyword.trim();

        return productRepository.searchProducts(
                searchKeyword, active, pageable
        ).map(this::convertToResponse);
    }

    public ProductResponse updateProduct(
            Long id,
            ProductRequest request
    ) {
        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Active product not found with ID: " + id
                ));

        Category category = findCategoryById(request.getCategoryId());

        product.setProductName(request.getProductName());
        product.setDescription(request.getDescription());
        product.setCostPrice(request.getCostPrice());
        product.setSellingPrice(request.getSellingPrice());
        product.setMinimumStock(request.getMinimumStock());
        product.setCategory(category);

        return convertToResponse(productRepository.save(product));
    }

    public void deleteProduct(Long id) {
        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Active product not found with ID: " + id
                ));

        product.setActive(false);
        productRepository.save(product);
    }

    public ProductResponse reactivateProduct(Long id) {
        Product product = findProductById(id);

        if (Boolean.TRUE.equals(product.getActive())) {
            throw new IllegalArgumentException(
                    "Product is already active"
            );
        }

        product.setActive(true);

        return convertToResponse(productRepository.save(product));
    }

    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product not found with ID: " + id
                ));
    }

    private Category findCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(
                        "Category not found with ID: " + id
                ));
    }

    private Pageable createPageable(
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("Page cannot be negative");
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
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "Direction must be either 'asc' or 'desc'"
            );
        }

        return PageRequest.of(
                page, size, Sort.by(sortDirection, sortBy)
        );
    }

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