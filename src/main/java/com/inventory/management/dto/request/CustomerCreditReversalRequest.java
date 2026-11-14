package com.inventory.management.dto.request;

import jakarta.validation.constraints.Size;

public class CustomerCreditReversalRequest {

    @Size(
            max = 500,
            message = "Remarks cannot exceed 500 characters"
    )
    private String remarks;

    public CustomerCreditReversalRequest() {
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}