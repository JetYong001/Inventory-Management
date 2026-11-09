package com.inventory.management.dto.response;

import com.inventory.management.enums.SalesInvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CustomerCreditApplicationResponse {

    private Long transactionId;
    private Long customerId;
    private Long salesInvoiceId;
    private String invoiceNumber;
    private BigDecimal appliedAmount;
    private BigDecimal availableCreditBalance;
    private BigDecimal outstandingBalance;
    private SalesInvoiceStatus invoiceStatus;
    private LocalDateTime createdAt;

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
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

    public BigDecimal getAppliedAmount() {
        return appliedAmount;
    }

    public void setAppliedAmount(BigDecimal appliedAmount) {
        this.appliedAmount = appliedAmount;
    }

    public BigDecimal getAvailableCreditBalance() {
        return availableCreditBalance;
    }

    public void setAvailableCreditBalance(BigDecimal availableCreditBalance) {
        this.availableCreditBalance = availableCreditBalance;
    }

    public BigDecimal getOutstandingBalance() {
        return outstandingBalance;
    }

    public void setOutstandingBalance(BigDecimal outstandingBalance) {
        this.outstandingBalance = outstandingBalance;
    }

    public SalesInvoiceStatus getInvoiceStatus() {
        return invoiceStatus;
    }

    public void setInvoiceStatus(SalesInvoiceStatus invoiceStatus) {
        this.invoiceStatus = invoiceStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}