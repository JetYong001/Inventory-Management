package com.inventory.management.repository;

import com.inventory.management.entity.SalesCreditNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalesCreditNoteRepository
        extends JpaRepository<SalesCreditNote, Long> {

    boolean existsBySalesReturnId(Long salesReturnId);

    Optional<SalesCreditNote> findByCreditNoteNumber(String creditNoteNumber);

    List<SalesCreditNote> findBySalesInvoiceId(Long salesInvoiceId);
}