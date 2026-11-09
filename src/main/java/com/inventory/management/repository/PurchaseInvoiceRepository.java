package com.inventory.management.repository;

import com.inventory.management.entity.PurchaseInvoice;
import com.inventory.management.enums.PurchaseInvoiceStatus;
import java.util.List;

import jakarta.persistence.LockModeType;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PurchaseInvoiceRepository
        extends JpaRepository<PurchaseInvoice, Long> {

    boolean existsByPurchase_Id(Long purchaseId);

    boolean existsByInvoiceNumber(String invoiceNumber);

    List<PurchaseInvoice> findAllByStatusOrderByInvoiceDateDesc(
            PurchaseInvoiceStatus status
    );

    Optional<PurchaseInvoice> findByPurchase_Id(Long purchaseId);

    Optional<PurchaseInvoice> findByInvoiceNumber(String invoiceNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM PurchaseInvoice i WHERE i.id = :id")
    Optional<PurchaseInvoice> findByIdForUpdate(@Param("id") Long id);
}