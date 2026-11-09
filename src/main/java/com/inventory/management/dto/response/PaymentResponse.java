package com.inventory.management.dto.response;

import com.inventory.management.enums.PaymentMethod;
import com.inventory.management.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PaymentResponse {

    private Long id;
    private String paymentNumber;

    private PaymentType paymentType;
    private PaymentMethod paymentMethod;

    private LocalDate paymentDate;
    private BigDecimal amount;

    private Long salesInvoiceId;
    private String salesInvoiceNumber;

    private Long purchaseInvoiceId;
    private String purchaseInvoiceNumber;

    private String referenceNumber;
    private String remarks;

    private LocalDateTime createdAt;

    public PaymentResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getSalesInvoiceId() {
        return salesInvoiceId;
    }

    public void setSalesInvoiceId(Long salesInvoiceId) {
        this.salesInvoiceId = salesInvoiceId;
    }

    public String getSalesInvoiceNumber() {
        return salesInvoiceNumber;
    }

    public void setSalesInvoiceNumber(String salesInvoiceNumber) {
        this.salesInvoiceNumber = salesInvoiceNumber;
    }

    public Long getPurchaseInvoiceId() {
        return purchaseInvoiceId;
    }

    public void setPurchaseInvoiceId(Long purchaseInvoiceId) {
        this.purchaseInvoiceId = purchaseInvoiceId;
    }

    public String getPurchaseInvoiceNumber() {
        return purchaseInvoiceNumber;
    }

    public void setPurchaseInvoiceNumber(String purchaseInvoiceNumber) {
        this.purchaseInvoiceNumber = purchaseInvoiceNumber;
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
}