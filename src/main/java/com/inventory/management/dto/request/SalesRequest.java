
package com.inventory.management.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class SalesRequest {

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @NotEmpty(message = "Sales must contain at least one item")
    @Valid
    private List<SalesItemRequest> items;

    public SalesRequest() {
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public List<SalesItemRequest> getItems() {
        return items;
    }

    public void setItems(List<SalesItemRequest> items) {
        this.items = items;
    }
}
