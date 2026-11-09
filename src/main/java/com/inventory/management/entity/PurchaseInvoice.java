package com.inventory.management.entity;

import com.inventory.management.enums.PurchaseInvoiceStatus;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "purchase_invoices",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "invoice_number"),
                @UniqueConstraint(columnNames = "purchase_id")
        }
)
public class PurchaseInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false, unique = true)
    private Purchase purchase;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseInvoiceStatus status = PurchaseInvoiceStatus.UNPAID;

    // Supplier snapshots
    @Column(length = 30)
    private String supplierCode;

    @Column(length = 150)
    private String supplierName;

    @Column(length = 100)
    private String supplierContactPerson;

    @Column(length = 30)
    private String supplierPhone;

    @Column(length = 150)
    private String supplierEmail;

    @Column(length = 500)
    private String supplierAddress;

    @OneToMany(
            mappedBy = "invoice",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PurchaseInvoiceItem> items = new ArrayList<>();

    public PurchaseInvoice() {
    }

    @PrePersist
    protected void onCreate() {
        if (invoiceDate == null) {
            invoiceDate = LocalDate.now();
        }

        if (status == null) {
            status = PurchaseInvoiceStatus.UNPAID;
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

    public Purchase getPurchase() {
        return purchase;
    }

    public void setPurchase(Purchase purchase) {
        this.purchase = purchase;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(LocalDate invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public PurchaseInvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(PurchaseInvoiceStatus status) {
        this.status = status;
    }

    public String getSupplierCode() {
        return supplierCode;
    }

    public void setSupplierCode(String supplierCode) {
        this.supplierCode = supplierCode;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getSupplierContactPerson() {
        return supplierContactPerson;
    }

    public void setSupplierContactPerson(String supplierContactPerson) {
        this.supplierContactPerson = supplierContactPerson;
    }

    public String getSupplierPhone() {
        return supplierPhone;
    }

    public void setSupplierPhone(String supplierPhone) {
        this.supplierPhone = supplierPhone;
    }

    public String getSupplierEmail() {
        return supplierEmail;
    }

    public void setSupplierEmail(String supplierEmail) {
        this.supplierEmail = supplierEmail;
    }

    public String getSupplierAddress() {
        return supplierAddress;
    }

    public void setSupplierAddress(String supplierAddress) {
        this.supplierAddress = supplierAddress;
    }

    public List<PurchaseInvoiceItem> getItems() {
        return items;
    }

    public void setItems(List<PurchaseInvoiceItem> items) {
        this.items = items;
    }
}