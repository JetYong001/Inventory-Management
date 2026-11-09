package com.inventory.management.repository;

import com.inventory.management.entity.PurchaseCreditNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseCreditNoteRepository
        extends JpaRepository<PurchaseCreditNote, Long> {

    boolean existsByPurchaseReturnId(Long purchaseReturnId);

    Optional<PurchaseCreditNote> findByCreditNoteNumber(String creditNoteNumber);

    List<PurchaseCreditNote> findByPurchaseInvoiceId(Long purchaseInvoiceId);
}