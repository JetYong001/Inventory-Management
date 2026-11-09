
package com.inventory.management.repository;

import com.inventory.management.entity.SupplierCreditTransaction;
import com.inventory.management.enums.SupplierCreditTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
