package com.inventory.management.repository;

import com.inventory.management.entity.CustomerCreditTransaction;
import com.inventory.management.enums.CustomerCreditTransactionType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface CustomerCreditTransactionRepository
        extends JpaRepository<CustomerCreditTransaction, Long> {

    List<CustomerCreditTransaction>
    findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    CustomerCreditTransaction findByReferenceNumberAndTransactionType(
            String referenceNumber,
            CustomerCreditTransactionType transactionType
    );

    boolean existsBySalesCreditNoteId(Long salesCreditNoteId);

    boolean existsByReversedTransaction_Id(Long transactionId);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query("""
            SELECT COALESCE(SUM(ABS(t.amount)), 0)
            FROM CustomerCreditTransaction t
            WHERE t.salesInvoice.id = :invoiceId
            AND t.transactionType = :transactionType
            """)
    BigDecimal getTotalAppliedCreditForSalesInvoice(
            @Param("invoiceId") Long invoiceId,
            @Param("transactionType")
            CustomerCreditTransactionType transactionType
    );

    @Query("""
            SELECT CASE
                WHEN COALESCE(SUM(t.amount), 0) < 0
                THEN -COALESCE(SUM(t.amount), 0)
                ELSE 0
            END
            FROM CustomerCreditTransaction t
            WHERE t.salesInvoice.id = :invoiceId
            AND t.transactionType IN :transactionTypes
            """)
    BigDecimal getNetAppliedCreditForSalesInvoice(
            @Param("invoiceId") Long invoiceId,
            @Param("transactionTypes")
            List<CustomerCreditTransactionType> transactionTypes
    );
}