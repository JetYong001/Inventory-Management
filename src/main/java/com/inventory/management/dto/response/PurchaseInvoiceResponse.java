
package com.inventory.management.dto.response;

import com.inventory.management.enums.PurchaseInvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class PurchaseInvoiceResponse {

    private Long id;
    private String invoiceNumber;

    private Long purchaseId;
    private String purchaseNumber;

    private LocalDate invoiceDate;

    private String supplierCode;
    private String supplierName;
    private String supplierContactPerson;
    private String supplierPhone;
    private String supplierEmail;
    private String supplierAddress;

    private BigDecimal totalAmount;
    private BigDecimal totalPaid;
    private BigDecimal totalCreditApplied;
    private BigDecimal outstandingAmount;

    private PurchaseInvoiceStatus status;

    private List<PurchaseInvoiceItemResponse> items;

    public PurchaseInvoiceResponse() {
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

    public Long getPurchaseId() {
        return purchaseId;
    }

    public void setPurchaseId(Long purchaseId) {
        this.purchaseId = purchaseId;
    }

    public String getPurchaseNumber() {
        return purchaseNumber;
    }

    public void setPurchaseNumber(String purchaseNumber) {
        this.purchaseNumber = purchaseNumber;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(LocalDate invoiceDate) {
        this.invoiceDate = invoiceDate;
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

    public String getSupplierContactPerson() {
        return supplierContactPerson;
    }

    public void setSupplierContactPerson(String supplierContactPerson) {
        this.supplierContactPerson = supplierContactPerson;
    }

    public String getSupplierPhone() {
        return supplierPhone;
    }

    public void setSupplierPhone(String supplierPhone) {
        this.supplierPhone = supplierPhone;
    }

    public String getSupplierEmail() {
        return supplierEmail;
    }

    public void setSupplierEmail(String supplierEmail) {
        this.supplierEmail = supplierEmail;
    }

    public String getSupplierAddress() {
        return supplierAddress;
    }

    public void setSupplierAddress(String supplierAddress) {
        this.supplierAddress = supplierAddress;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getTotalPaid() {
        return totalPaid;
    }

    public void setTotalPaid(BigDecimal totalPaid) {
        this.totalPaid = totalPaid;
    }

    public BigDecimal getTotalCreditApplied() {
        return totalCreditApplied;
    }

    public void setTotalCreditApplied(BigDecimal totalCreditApplied) {
        this.totalCreditApplied = totalCreditApplied;
    }

    public BigDecimal getOutstandingAmount() {
        return outstandingAmount;
    }

    public void setOutstandingAmount(BigDecimal outstandingAmount) {
        this.outstandingAmount = outstandingAmount;
    }

    public PurchaseInvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(PurchaseInvoiceStatus status) {
        this.status = status;
    }

    public List<PurchaseInvoiceItemResponse> getItems() {
        return items;
    }

    public void setItems(List<PurchaseInvoiceItemResponse> items) {
        this.items = items;
    }
}
