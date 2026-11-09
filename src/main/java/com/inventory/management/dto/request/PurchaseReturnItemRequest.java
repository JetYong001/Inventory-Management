package com.inventory.management.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class PurchaseReturnItemRequest {

    @NotNull(message = "Purchase item ID is required")
    private Long purchaseItemId;

    @NotNull(message = "Return quantity is required")
    @Min(value = 1, message = "Return quantity must be at least 1")
    private Integer quantity;

    public Long getPurchaseItemId() {
        return purchaseItemId;
    }

    public void setPurchaseItemId(Long purchaseItemId) {
        this.purchaseItemId = purchaseItemId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}