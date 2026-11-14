package com.inventory.management.repository;

import com.inventory.management.entity.SupplierCreditTransaction;
import com.inventory.management.enums.SupplierCreditTransactionType;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierCreditTransactionRepository
        extends JpaRepository<SupplierCreditTransaction, Long> {

    List<SupplierCreditTransaction>
    findBySupplierIdOrderByCreatedAtDesc(Long supplierId);

    List<SupplierCreditTransaction>
    findByPurchaseInvoice_IdOrderByCreatedAtDesc(Long purchaseInvoiceId);

    List<SupplierCreditTransaction>
    findByPurchaseCreditNote_Id(Long purchaseCreditNoteId);

    Optional<SupplierCreditTransaction>
    findByReferenceNumberAndTransactionType(
            String referenceNumber,
            SupplierCreditTransactionType transactionType
    );

    boolean existsByPurchaseCreditNoteId(Long purchaseCreditNoteId);

    boolean existsByReversedTransaction_Id(Long transactionId);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t
            FROM SupplierCreditTransaction t
            WHERE t.id = :transactionId
            """)
    Optional<SupplierCreditTransaction> findByIdForUpdate(
            @Param("transactionId") Long transactionId
    );
}