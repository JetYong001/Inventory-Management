package com.inventory.management.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_return_items")
public class PurchaseReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_return_id", nullable = false)
    private PurchaseReturn purchaseReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_item_id", nullable = false)
    private PurchaseItem purchaseItem;

    @Column(length = 50)
    private String productCode;

    @Column(nullable = false, length = 150)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    public PurchaseReturnItem() {
    }

    public Long getId() {
        return id;
    }

    public PurchaseReturn getPurchaseReturn() {
        return purchaseReturn;
    }

    public void setPurchaseReturn(PurchaseReturn purchaseReturn) {
        this.purchaseReturn = purchaseReturn;
    }

    public PurchaseItem getPurchaseItem() {
        return purchaseItem;
    }

    public void setPurchaseItem(PurchaseItem purchaseItem) {
        this.purchaseItem = purchaseItem;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }
}