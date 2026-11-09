
package com.inventory.management.dto.response;

import java.math.BigDecimal;

public class InventorySummaryResponse {

    private Long totalProducts;
    private Long activeProducts;
    private Long inactiveProducts;
    private Long totalStockQuantity;
    private BigDecimal totalInventoryValue;
    private Long lowStockCount;
    private Long outOfStockCount;

    public InventorySummaryResponse() {
    }

    public Long getTotalProducts() {
        return totalProducts;
    }

    public void setTotalProducts(Long totalProducts) {
        this.totalProducts = totalProducts;
    }

    public Long getActiveProducts() {
        return activeProducts;
    }

    public void setActiveProducts(Long activeProducts) {
        this.activeProducts = activeProducts;
    }

    public Long getInactiveProducts() {
        return inactiveProducts;
    }

    public void setInactiveProducts(Long inactiveProducts) {
        this.inactiveProducts = inactiveProducts;
    }

    public Long getTotalStockQuantity() {
        return totalStockQuantity;
    }

    public void setTotalStockQuantity(Long totalStockQuantity) {
        this.totalStockQuantity = totalStockQuantity;
    }

    public BigDecimal getTotalInventoryValue() {
        return totalInventoryValue;
    }

    public void setTotalInventoryValue(BigDecimal totalInventoryValue) {
        this.totalInventoryValue = totalInventoryValue;
    }

    public Long getLowStockCount() {
        return lowStockCount;
    }

    public void setLowStockCount(Long lowStockCount) {
        this.lowStockCount = lowStockCount;
    }

    public Long getOutOfStockCount() {
        return outOfStockCount;
    }

    public void setOutOfStockCount(Long outOfStockCount) {
        this.outOfStockCount = outOfStockCount;
    }
}
