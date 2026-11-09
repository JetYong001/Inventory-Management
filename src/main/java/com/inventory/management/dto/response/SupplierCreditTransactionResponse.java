package com.inventory.management.dto.response;

import com.inventory.management.enums.SupplierCreditTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SupplierCreditTransactionResponse {

    private Long id;
    private SupplierCreditTransactionType transactionType;
    private BigDecimal amount;
    private String referenceNumber;
    private String remarks;
    private Long purchaseCreditNoteId;
    private String creditNoteNumber;
    private Long purchaseInvoiceId;
    private String invoiceNumber;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SupplierCreditTransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(SupplierCreditTransactionType transactionType) {
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

    public Long getPurchaseCreditNoteId() {
        return purchaseCreditNoteId;
    }

    public void setPurchaseCreditNoteId(Long purchaseCreditNoteId) {
        this.purchaseCreditNoteId = purchaseCreditNoteId;
    }

    public String getCreditNoteNumber() {
        return creditNoteNumber;
    }

    public void setCreditNoteNumber(String creditNoteNumber) {
        this.creditNoteNumber = creditNoteNumber;
    }

    public Long getPurchaseInvoiceId() {
        return purchaseInvoiceId;
    }

    public void setPurchaseInvoiceId(Long purchaseInvoiceId) {
        this.purchaseInvoiceId = purchaseInvoiceId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}