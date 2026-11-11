package com.inventory.management.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public class GoodsReceivedNoteResponse {

    private Long id;
    private String grnNumber;
    private Long purchaseId;
    private String purchaseNumber;
    private String supplierName;
    private LocalDateTime receivedAt;
    private List<GoodsReceivedNoteItemResponse> items;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGrnNumber() {
        return grnNumber;
    }

    public void setGrnNumber(String grnNumber) {
        this.grnNumber = grnNumber;
    }

    public Long getPurchaseId() {
        return purchaseId;
    }

    public void setPurchaseId(Long purchaseId) {
        this.purchaseId = purchaseId;
    }

    public String getPurchaseNumber() {
        return purchaseNumber;
    }

    public void setPurchaseNumber(String purchaseNumber) {
        this.purchaseNumber = purchaseNumber;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public List<GoodsReceivedNoteItemResponse> getItems() {
        return items;
    }

    public void setItems(List<GoodsReceivedNoteItemResponse> items) {
        this.items = items;
    }
}