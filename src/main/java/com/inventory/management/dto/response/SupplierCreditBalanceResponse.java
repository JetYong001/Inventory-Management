package com.inventory.management.dto.response;

import java.math.BigDecimal;
import java.util.List;

public class SupplierCreditBalanceResponse {

    private Long supplierId;
    private String supplierCode;
    private String supplierName;

    private BigDecimal totalCreditIssued;
    private BigDecimal totalCreditApplied;
    private BigDecimal totalRefunded;
    private BigDecimal availableCreditBalance;

    private List<SupplierCreditTransactionResponse> transactions;

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
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

    public BigDecimal getTotalCreditIssued() {
        return totalCreditIssued;
    }

    public void setTotalCreditIssued(BigDecimal totalCreditIssued) {
        this.totalCreditIssued = totalCreditIssued;
    }

    public BigDecimal getTotalCreditApplied() {
        return totalCreditApplied;
    }

    public void setTotalCreditApplied(BigDecimal totalCreditApplied) {
        this.totalCreditApplied = totalCreditApplied;
    }

    public BigDecimal getTotalRefunded() {
        return totalRefunded;
    }

    public void setTotalRefunded(BigDecimal totalRefunded) {
        this.totalRefunded = totalRefunded;
    }

    public BigDecimal getAvailableCreditBalance() {
        return availableCreditBalance;
    }

    public void setAvailableCreditBalance(BigDecimal availableCreditBalance) {
        this.availableCreditBalance = availableCreditBalance;
    }

    public List<SupplierCreditTransactionResponse> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<SupplierCreditTransactionResponse> transactions) {
        this.transactions = transactions;
    }
}