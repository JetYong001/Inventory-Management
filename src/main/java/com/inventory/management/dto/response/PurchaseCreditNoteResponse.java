package com.inventory.management.dto.response;

import com.inventory.management.enums.CreditNoteStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PurchaseCreditNoteResponse(
        Long id,
        String creditNoteNumber,
        Long purchaseReturnId,
        String returnNumber,
        Long purchaseInvoiceId,
        String invoiceNumber,
        BigDecimal totalAmount,
        CreditNoteStatus status,
        LocalDateTime creditNoteDate,
        LocalDateTime createdAt
) {
}