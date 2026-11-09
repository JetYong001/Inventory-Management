
package com.inventory.management.controller;

import com.inventory.management.entity.SalesCreditNote;
import com.inventory.management.enums.CreditNoteStatus;
import com.inventory.management.repository.SalesCreditNoteRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/sales-credit-notes")
@CrossOrigin(origins = "*")
public class SalesCreditNoteController {

    private final SalesCreditNoteRepository salesCreditNoteRepository;

    public SalesCreditNoteController(
            SalesCreditNoteRepository salesCreditNoteRepository) {
        this.salesCreditNoteRepository = salesCreditNoteRepository;
    }

    @GetMapping
    public ResponseEntity<List<CreditNoteResponse>> getAllCreditNotes() {
        List<CreditNoteResponse> responses = salesCreditNoteRepository
                .findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CreditNoteResponse> getCreditNoteById(
            @PathVariable Long id) {

        return salesCreditNoteRepository.findById(id)
                .map(this::convertToResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{creditNoteNumber}")
    public ResponseEntity<CreditNoteResponse> getCreditNoteByNumber(
            @PathVariable String creditNoteNumber) {

        return salesCreditNoteRepository
                .findByCreditNoteNumber(creditNoteNumber)
                .map(this::convertToResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sales-invoice/{salesInvoiceId}")
    public ResponseEntity<List<CreditNoteResponse>> getCreditNotesBySalesInvoice(
            @PathVariable Long salesInvoiceId) {

        List<CreditNoteResponse> responses = salesCreditNoteRepository
                .findBySalesInvoiceId(salesInvoiceId)
                .stream()
                .map(this::convertToResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    private CreditNoteResponse convertToResponse(SalesCreditNote creditNote) {
        return new CreditNoteResponse(
                creditNote.getId(),
                creditNote.getCreditNoteNumber(),
                creditNote.getSalesReturn().getId(),
                creditNote.getSalesReturn().getReturnNumber(),
                creditNote.getSalesInvoice().getId(),
                creditNote.getSalesInvoice().getInvoiceNumber(),
                creditNote.getTotalAmount(),
                creditNote.getStatus(),
                creditNote.getCreditNoteDate(),
                creditNote.getCreatedAt()
        );
    }

    public record CreditNoteResponse(
            Long id,
            String creditNoteNumber,
            Long salesReturnId,
            String returnNumber,
            Long salesInvoiceId,
            String invoiceNumber,
            BigDecimal totalAmount,
            CreditNoteStatus status,
            LocalDateTime creditNoteDate,
            LocalDateTime createdAt
    ) {}
}
