package com.inventory.management.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class SalesReturnItemRequest {

    @NotNull(message = "Sales item ID is required")
    private Long salesItemId;

    @NotNull(message = "Return quantity is required")
    @Min(value = 1, message = "Return quantity must be at least 1")
    private Integer quantity;

    public Long getSalesItemId() {
        return salesItemId;
    }

    public void setSalesItemId(Long salesItemId) {
        this.salesItemId = salesItemId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}