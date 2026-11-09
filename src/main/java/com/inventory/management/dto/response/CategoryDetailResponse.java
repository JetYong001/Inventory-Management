package com.inventory.management.dto.response;

import java.util.List;

public class CategoryDetailResponse {

    private Long id;
    private String categoryName;
    private String description;
    private List<CategoryProductResponse> products;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<CategoryProductResponse> getProducts() {
        return products;
    }

    public void setProducts(List<CategoryProductResponse> products) {
        this.products = products;
    }
}