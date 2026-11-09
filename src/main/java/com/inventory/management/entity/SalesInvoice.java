
package com.inventory.management.entity;

import com.inventory.management.enums.SalesInvoiceStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "sales_invoices",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_invoice_sales",
                        columnNames = "sales_id"
                ),
                @UniqueConstraint(
                        name = "uk_invoice_number",
                        columnNames = "invoice_number"
                )
        }
)
public class SalesInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "invoice_number",
            nullable = false,
            unique = true,
            length = 50
    )
    private String invoiceNumber;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "sales_id",
            nullable = false,
            unique = true
    )
    private Sales sales;

    @Column(nullable = false)
    private LocalDateTime invoiceDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalesInvoiceStatus status = SalesInvoiceStatus.UNPAID;

    // Customer snapshot

    @Column(nullable = false, length = 30)
    private String customerCode;

    @Column(nullable = false, length = 150)
    private String customerName;

    @Column(length = 30)
    private String customerPhone;

    @Column(length = 150)
    private String customerEmail;

    @Column(length = 500)
    private String customerAddress;

    // Invoice items

    @OneToMany(
            mappedBy = "invoice",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<SalesInvoiceItem> items = new ArrayList<>();

    public SalesInvoice() {
    }

    @PrePersist
    protected void onCreate() {
        if (invoiceDate == null) {
            invoiceDate = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public Sales getSales() {
        return sales;
    }

    public void setSales(Sales sales) {
        this.sales = sales;
    }

    public LocalDateTime getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(LocalDateTime invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public SalesInvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(SalesInvoiceStatus status) {
        this.status = status;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public void setCustomerAddress(String customerAddress) {
        this.customerAddress = customerAddress;
    }

    public List<SalesInvoiceItem> getItems() {
        return items;
    }

    public void setItems(List<SalesInvoiceItem> items) {
        this.items = items;
    }
}
