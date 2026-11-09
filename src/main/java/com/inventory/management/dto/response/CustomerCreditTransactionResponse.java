package com.inventory.management.dto.response;

import com.inventory.management.enums.CustomerCreditTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CustomerCreditTransactionResponse {

    private Long id;
    private CustomerCreditTransactionType transactionType;
    private BigDecimal amount;
    private String referenceNumber;
    private String remarks;
    private LocalDateTime createdAt;

    private Long salesCreditNoteId;
    private String creditNoteNumber;

    private Long salesInvoiceId;
    private String invoiceNumber;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getSalesCreditNoteId() {
        return salesCreditNoteId;
    }

    public void setSalesCreditNoteId(Long salesCreditNoteId) {
        this.salesCreditNoteId = salesCreditNoteId;
    }

    public String getCreditNoteNumber() {
        return creditNoteNumber;
    }

    public void setCreditNoteNumber(String creditNoteNumber) {
        this.creditNoteNumber = creditNoteNumber;
    }

    public Long getSalesInvoiceId() {
        return salesInvoiceId;
    }

    public void setSalesInvoiceId(Long salesInvoiceId) {
        this.salesInvoiceId = salesInvoiceId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }
}