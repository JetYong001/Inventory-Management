package com.inventory.management.dto.request;

import jakarta.validation.constraints.Size;

public class SupplierCreditReversalRequest {

    @Size(max = 500, message = "Remarks cannot exceed 500 characters")
    private String remarks;

    public SupplierCreditReversalRequest() {
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}