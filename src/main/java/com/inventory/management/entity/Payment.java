package com.inventory.management.entity;

import com.inventory.management.enums.PaymentMethod;
import com.inventory.management.enums.PaymentType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_number",
                        columnNames = "payment_number"
                )
        }
)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "payment_number",
            nullable = false,
            unique = true,
            length = 50
    )
    private String paymentNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_invoice_id")
    private SalesInvoice salesInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_invoice_id")
    private PurchaseInvoice purchaseInvoice;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(length = 500)
    private String remarks;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Payment() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getPaymentNumber() {
        return paymentNumber;
    }

    public void setPaymentNumber(String paymentNumber) {
        this.paymentNumber = paymentNumber;
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public SalesInvoice getSalesInvoice() {
        return salesInvoice;
    }

    public void setSalesInvoice(SalesInvoice salesInvoice) {
        this.salesInvoice = salesInvoice;
    }

    public PurchaseInvoice getPurchaseInvoice() {
        return purchaseInvoice;
    }

    public void setPurchaseInvoice(PurchaseInvoice purchaseInvoice) {
        this.purchaseInvoice = purchaseInvoice;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}