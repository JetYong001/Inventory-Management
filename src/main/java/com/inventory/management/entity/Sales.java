
package com.inventory.management.entity;

import com.inventory.management.enums.SalesStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales")
public class Sales {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String salesNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private LocalDateTime salesDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalesStatus status = SalesStatus.DRAFT;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(
            mappedBy = "sales",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<SalesItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        salesDate = LocalDateTime.now();
    }

    public Sales() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSalesNumber() {
        return salesNumber;
    }

    public void setSalesNumber(String salesNumber) {
        this.salesNumber = salesNumber;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public LocalDateTime getSalesDate() {
        return salesDate;
    }

    public void setSalesDate(LocalDateTime salesDate) {
        this.salesDate = salesDate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public SalesStatus getStatus() {
        return status;
    }

    public void setStatus(SalesStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<SalesItem> getItems() {
        return items;
    }

    public void setItems(List<SalesItem> items) {
        this.items = items;
    }
}
