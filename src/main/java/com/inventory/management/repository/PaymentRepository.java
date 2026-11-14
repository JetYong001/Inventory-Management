package com.inventory.management.repository;

import com.inventory.management.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByPaymentNumber(String paymentNumber);

    boolean existsBySalesInvoice_Id(Long salesInvoiceId);

    boolean existsByPurchaseInvoice_Id(Long purchaseInvoiceId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findAllBySalesInvoice_IdOrderByPaymentDateDesc(Long salesInvoiceId);

    List<Payment> findAllByPurchaseInvoice_IdOrderByPaymentDateDesc(Long purchaseInvoiceId);

    List<Payment> findAllByOrderByPaymentDateDesc();

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.salesInvoice.id = :invoiceId
            """)
    BigDecimal getTotalSalesInvoicePayments(
            @Param("invoiceId") Long invoiceId
    );

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.purchaseInvoice.id = :invoiceId
            """)
    BigDecimal getTotalPurchaseInvoicePayments(
            @Param("invoiceId") Long invoiceId
    );
}