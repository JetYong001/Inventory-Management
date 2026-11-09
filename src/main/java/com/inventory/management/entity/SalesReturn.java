package com.inventory.management.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales_returns")
public class SalesReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String returnNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_id", nullable = false)
    private Sales sales;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_invoice_id", nullable = false)
    private SalesInvoice salesInvoice;

    @Column(nullable = false)
    private LocalDateTime returnDate;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @OneToMany(
            mappedBy = "salesReturn",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<SalesReturnItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (returnDate == null) {
            returnDate = LocalDateTime.now();
        }
        createdAt = LocalDateTime.now();
    }

    public SalesReturn() {
    }

    public Long getId() {
        return id;
    }

    public String getReturnNumber() {
        return returnNumber;
    }

    public void setReturnNumber(String returnNumber) {
        this.returnNumber = returnNumber;
    }

    public Sales getSales() {
        return sales;
    }

    public void setSales(Sales sales) {
        this.sales = sales;
    }

    public SalesInvoice getSalesInvoice() {
        return salesInvoice;
    }

    public void setSalesInvoice(SalesInvoice salesInvoice) {
        this.salesInvoice = salesInvoice;
    }

    public LocalDateTime getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDateTime returnDate) {
        this.returnDate = returnDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public List<SalesReturnItem> getItems() {
        return items;
    }

    public void setItems(List<SalesReturnItem> items) {
        this.items = items;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}