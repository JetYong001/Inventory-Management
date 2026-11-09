package com.inventory.management.dto.response;

import java.math.BigDecimal;
import java.util.List;

public class CustomerCreditBalanceResponse {

    private Long customerId;
    private String customerCode;
    private String customerName;

    private BigDecimal totalCreditIssued;
    private BigDecimal totalCreditApplied;
    private BigDecimal totalRefunded;
    private BigDecimal availableCreditBalance;

    private List<CustomerCreditTransactionResponse> transactions;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
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

    public List<CustomerCreditTransactionResponse> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<CustomerCreditTransactionResponse> transactions) {
        this.transactions = transactions;
    }
}