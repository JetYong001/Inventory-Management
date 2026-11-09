package com.inventory.management.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class PurchaseReturnRequest {

    @NotNull(message = "Purchase ID is required")
    private Long purchaseId;

    @NotBlank(message = "Return reason is required")
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;

    @NotEmpty(message = "Purchase return must contain at least one item")
    @Valid
    private List<PurchaseReturnItemRequest> items;

    public Long getPurchaseId() {
        return purchaseId;
    }

    public void setPurchaseId(Long purchaseId) {
        this.purchaseId = purchaseId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<PurchaseReturnItemRequest> getItems() {
        return items;
    }

    public void setItems(List<PurchaseReturnItemRequest> items) {
        this.items = items;
    }
}