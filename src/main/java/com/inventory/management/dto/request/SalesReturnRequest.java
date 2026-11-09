package com.inventory.management.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class SalesReturnRequest {

    @NotNull(message = "Sales ID is required")
    private Long salesId;

    @NotBlank(message = "Return reason is required")
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;

    @NotEmpty(message = "Sales return must contain at least one item")
    @Valid
    private List<SalesReturnItemRequest> items;

    public Long getSalesId() {
        return salesId;
    }

    public void setSalesId(Long salesId) {
        this.salesId = salesId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<SalesReturnItemRequest> getItems() {
        return items;
    }

    public void setItems(List<SalesReturnItemRequest> items) {
        this.items = items;
    }
}