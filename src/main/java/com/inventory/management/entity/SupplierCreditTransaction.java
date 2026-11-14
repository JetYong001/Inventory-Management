package com.inventory.management.entity;

import com.inventory.management.enums.SupplierCreditTransactionType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "supplier_credit_transactions")
public class SupplierCreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_credit_note_id")
    private PurchaseCreditNote purchaseCreditNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_invoice_id")
    private PurchaseInvoice purchaseInvoice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SupplierCreditTransactionType transactionType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 50)
    private String referenceNumber;

    @Column(length = 500)
    private String remarks;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_transaction_id", unique = true)
    private SupplierCreditTransaction reversedTransaction;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public SupplierCreditTransaction() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public PurchaseCreditNote getPurchaseCreditNote() {
        return purchaseCreditNote;
    }

    public void setPurchaseCreditNote(
            PurchaseCreditNote purchaseCreditNote
    ) {
        this.purchaseCreditNote = purchaseCreditNote;
    }

    public PurchaseInvoice getPurchaseInvoice() {
        return purchaseInvoice;
    }

    public void setPurchaseInvoice(PurchaseInvoice purchaseInvoice) {
        this.purchaseInvoice = purchaseInvoice;
    }

    public SupplierCreditTransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(
            SupplierCreditTransactionType transactionType
    ) {
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public SupplierCreditTransaction getReversedTransaction() {
        return reversedTransaction;
    }

    public void setReversedTransaction(
            SupplierCreditTransaction reversedTransaction
    ) {
        this.reversedTransaction = reversedTransaction;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}