package com.inventory.management.controller;

import com.inventory.management.dto.response.PurchaseCreditNoteResponse;
import com.inventory.management.entity.PurchaseCreditNote;
import com.inventory.management.repository.PurchaseCreditNoteRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-credit-notes")
@CrossOrigin(origins = "*")
public class PurchaseCreditNoteController {

    private final PurchaseCreditNoteRepository purchaseCreditNoteRepository;

    public PurchaseCreditNoteController(
            PurchaseCreditNoteRepository purchaseCreditNoteRepository) {
        this.purchaseCreditNoteRepository = purchaseCreditNoteRepository;
    }

    @GetMapping
    public ResponseEntity<List<PurchaseCreditNoteResponse>> getAllCreditNotes() {

        List<PurchaseCreditNoteResponse> responses =
                purchaseCreditNoteRepository.findAll()
                        .stream()
                        .map(this::convertToResponse)
                        .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseCreditNoteResponse> getCreditNoteById(
            @PathVariable Long id) {

        PurchaseCreditNote creditNote = purchaseCreditNoteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase credit note not found"
                ));

        return ResponseEntity.ok(convertToResponse(creditNote));
    }

    @GetMapping("/number/{creditNoteNumber}")
    public ResponseEntity<PurchaseCreditNoteResponse> getCreditNoteByNumber(
            @PathVariable String creditNoteNumber) {

        PurchaseCreditNote creditNote =
                purchaseCreditNoteRepository
                        .findByCreditNoteNumber(creditNoteNumber)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Purchase credit note not found"
                        ));

        return ResponseEntity.ok(convertToResponse(creditNote));
    }

    @GetMapping("/purchase-invoice/{purchaseInvoiceId}")
    public ResponseEntity<List<PurchaseCreditNoteResponse>> getCreditNotesByInvoice(
            @PathVariable Long purchaseInvoiceId) {

        List<PurchaseCreditNoteResponse> responses =
                purchaseCreditNoteRepository
                        .findByPurchaseInvoiceId(purchaseInvoiceId)
                        .stream()
                        .map(this::convertToResponse)
                        .toList();

        return ResponseEntity.ok(responses);
    }

    private PurchaseCreditNoteResponse convertToResponse(
            PurchaseCreditNote creditNote) {

        return new PurchaseCreditNoteResponse(
                creditNote.getId(),
                creditNote.getCreditNoteNumber(),
                creditNote.getPurchaseReturn().getId(),
                creditNote.getPurchaseReturn().getReturnNumber(),
                creditNote.getPurchaseInvoice().getId(),
                creditNote.getPurchaseInvoice().getInvoiceNumber(),
                creditNote.getTotalAmount(),
                creditNote.getStatus(),
                creditNote.getCreditNoteDate(),
                creditNote.getCreatedAt()
        );
    }
}