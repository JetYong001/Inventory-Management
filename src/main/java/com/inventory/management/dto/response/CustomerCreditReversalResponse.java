package com.inventory.management.dto.response;

import com.inventory.management.enums.SalesInvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CustomerCreditReversalResponse {

    private Long reversalTransactionId;
    private String reversalReferenceNumber;

    private Long originalTransactionId;
    private String originalReferenceNumber;

    private Long customerId;
    private Long salesInvoiceId;
    private String invoiceNumber;

    private BigDecimal reversedAmount;
    private BigDecimal availableCreditBalance;
    private BigDecimal outstandingBalance;

    private SalesInvoiceStatus invoiceStatus;
    private LocalDateTime createdAt;

    public CustomerCreditReversalResponse() {
    }

    public Long getReversalTransactionId() {
        return reversalTransactionId;
    }

    public void setReversalTransactionId(Long reversalTransactionId) {
        this.reversalTransactionId = reversalTransactionId;
    }

    public String getReversalReferenceNumber() {
        return reversalReferenceNumber;
    }

    public void setReversalReferenceNumber(String reversalReferenceNumber) {
        this.reversalReferenceNumber = reversalReferenceNumber;
    }

    public Long getOriginalTransactionId() {
        return originalTransactionId;
    }

    public void setOriginalTransactionId(Long originalTransactionId) {
        this.originalTransactionId = originalTransactionId;
    }

    public String getOriginalReferenceNumber() {
        return originalReferenceNumber;
    }

    public void setOriginalReferenceNumber(String originalReferenceNumber) {
        this.originalReferenceNumber = originalReferenceNumber;
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

    public BigDecimal getReversedAmount() {
        return reversedAmount;
    }

    public void setReversedAmount(BigDecimal reversedAmount) {
        this.reversedAmount = reversedAmount;
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