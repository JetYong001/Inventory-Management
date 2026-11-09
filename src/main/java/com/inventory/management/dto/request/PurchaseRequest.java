
package com.inventory.management.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class PurchaseRequest {

    @NotNull(message = "Supplier ID is required")
    private Long supplierId;

    @NotEmpty(message = "Purchase must contain at least one item")
    @Valid
    private List<PurchaseItemRequest> items;

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public List<PurchaseItemRequest> getItems() {
        return items;
    }

    public void setItems(List<PurchaseItemRequest> items) {
        this.items = items;
    }
}
