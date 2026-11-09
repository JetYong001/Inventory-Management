
package com.inventory.management.dto.response;

import com.inventory.management.enums.SalesStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class SalesResponse {

    private Long id;
    private String salesNumber;
    private Long customerId;
    private String customerName;
    private LocalDateTime salesDate;
    private BigDecimal totalAmount;
    private SalesStatus status;
    private List<SalesItemResponse> items;

    public SalesResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSalesNumber() {
        return salesNumber;
    }

    public void setSalesNumber(String salesNumber) {
        this.salesNumber = salesNumber;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public LocalDateTime getSalesDate() {
        return salesDate;
    }

    public void setSalesDate(LocalDateTime salesDate) {
        this.salesDate = salesDate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public SalesStatus getStatus() {
        return status;
    }

    public void setStatus(SalesStatus status) {
        this.status = status;
    }

    public List<SalesItemResponse> getItems() {
        return items;
    }

    public void setItems(List<SalesItemResponse> items) {
        this.items = items;
    }
}
