package com.inventory.management.dto.response;

import com.inventory.management.enums.PurchaseInvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SupplierCreditReversalResponse {

    private Long reversalTransactionId;
    private String reversalReferenceNumber;

    private Long originalTransactionId;
    private String originalReferenceNumber;

    private Long supplierId;
    private Long purchaseInvoiceId;
    private String invoiceNumber;

    private BigDecimal reversedAmount;
    private BigDecimal availableCreditBalance;
    private BigDecimal outstandingBalance;

    private PurchaseInvoiceStatus invoiceStatus;
    private LocalDateTime createdAt;

    public SupplierCreditReversalResponse() {
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

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
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

    public PurchaseInvoiceStatus getInvoiceStatus() {
        return invoiceStatus;
    }

    public void setInvoiceStatus(PurchaseInvoiceStatus invoiceStatus) {
        this.invoiceStatus = invoiceStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}