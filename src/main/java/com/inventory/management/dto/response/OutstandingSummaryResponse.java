package com.inventory.management.dto.response;

import java.math.BigDecimal;

public class OutstandingSummaryResponse {

    private BigDecimal totalCustomerOutstanding;
    private BigDecimal totalSupplierOutstanding;

    private long unpaidSalesInvoiceCount;
    private long unpaidPurchaseInvoiceCount;

    private long totalOutstandingInvoices;

    public BigDecimal getTotalCustomerOutstanding() {
        return totalCustomerOutstanding;
    }

    public void setTotalCustomerOutstanding(BigDecimal totalCustomerOutstanding) {
        this.totalCustomerOutstanding = totalCustomerOutstanding;
    }

    public BigDecimal getTotalSupplierOutstanding() {
        return totalSupplierOutstanding;
    }

    public void setTotalSupplierOutstanding(BigDecimal totalSupplierOutstanding) {
        this.totalSupplierOutstanding = totalSupplierOutstanding;
    }

    public long getUnpaidSalesInvoiceCount() {
        return unpaidSalesInvoiceCount;
    }

    public void setUnpaidSalesInvoiceCount(long unpaidSalesInvoiceCount) {
        this.unpaidSalesInvoiceCount = unpaidSalesInvoiceCount;
    }

    public long getUnpaidPurchaseInvoiceCount() {
        return unpaidPurchaseInvoiceCount;
    }

    public void setUnpaidPurchaseInvoiceCount(long unpaidPurchaseInvoiceCount) {
        this.unpaidPurchaseInvoiceCount = unpaidPurchaseInvoiceCount;
    }

    public long getTotalOutstandingInvoices() {
        return totalOutstandingInvoices;
    }

    public void setTotalOutstandingInvoices(long totalOutstandingInvoices) {
        this.totalOutstandingInvoices = totalOutstandingInvoices;
    }
}