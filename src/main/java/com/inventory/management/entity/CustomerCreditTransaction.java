package com.inventory.management.entity;

import com.inventory.management.enums.CustomerCreditTransactionType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_credit_transactions")
public class CustomerCreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_credit_note_id")
    private SalesCreditNote salesCreditNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_invoice_id")
    private SalesInvoice salesInvoice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CustomerCreditTransactionType transactionType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 50)
    private String referenceNumber;

    @Column(length = 500)
    private String remarks;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public CustomerCreditTransaction() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public SalesCreditNote getSalesCreditNote() {
        return salesCreditNote;
    }

    public void setSalesCreditNote(SalesCreditNote salesCreditNote) {
        this.salesCreditNote = salesCreditNote;
    }

    public SalesInvoice getSalesInvoice() {
        return salesInvoice;
    }

    public void setSalesInvoice(SalesInvoice salesInvoice) {
        this.salesInvoice = salesInvoice;
    }

    public CustomerCreditTransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(CustomerCreditTransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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