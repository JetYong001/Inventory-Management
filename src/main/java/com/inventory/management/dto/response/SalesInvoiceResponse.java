
package com.inventory.management.dto.response;

import com.inventory.management.enums.SalesInvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class SalesInvoiceResponse {

    private Long id;
    private String invoiceNumber;

    private Long salesId;
    private String salesNumber;

    private LocalDateTime invoiceDate;

    private String customerCode;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String customerAddress;

    private BigDecimal totalAmount;
    private SalesInvoiceStatus status;

    private List<SalesInvoiceItemResponse> items;

    public SalesInvoiceResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public Long getSalesId() {
        return salesId;
    }

    public void setSalesId(Long salesId) {
        this.salesId = salesId;
    }

    public String getSalesNumber() {
        return salesNumber;
    }

    public void setSalesNumber(String salesNumber) {
        this.salesNumber = salesNumber;
    }

    public LocalDateTime getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(LocalDateTime invoiceDate) {
        this.invoiceDate = invoiceDate;
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

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public void setCustomerAddress(String customerAddress) {
        this.customerAddress = customerAddress;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public SalesInvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(SalesInvoiceStatus status) {
        this.status = status;
    }

    public List<SalesInvoiceItemResponse> getItems() {
        return items;
    }

    public void setItems(List<SalesInvoiceItemResponse> items) {
        this.items = items;
    }
}
