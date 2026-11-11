package com.inventory.management.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class GoodsReceivedNoteRequest {

    @NotNull
    private Long purchaseId;

    @NotBlank
    private String requestKey;

    @NotEmpty
    @Valid
    private List<GoodsReceivedNoteItemRequest> items;

    public Long getPurchaseId() {
        return purchaseId;
    }

    public void setPurchaseId(Long purchaseId) {
        this.purchaseId = purchaseId;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String requestKey) {
        this.requestKey = requestKey;
    }

    public List<GoodsReceivedNoteItemRequest> getItems() {
        return items;
    }

    public void setItems(List<GoodsReceivedNoteItemRequest> items) {
        this.items = items;
    }
}