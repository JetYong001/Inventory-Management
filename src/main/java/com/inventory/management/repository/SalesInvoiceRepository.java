package com.inventory.management.repository;

import com.inventory.management.entity.SalesInvoice;
import com.inventory.management.enums.SalesInvoiceStatus;
import java.util.List;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SalesInvoiceRepository
        extends JpaRepository<SalesInvoice, Long> {

    boolean existsBySales_Id(Long salesId);

    boolean existsByInvoiceNumber(String invoiceNumber);

    List<SalesInvoice> findAllByStatusOrderByInvoiceDateDesc(
            SalesInvoiceStatus status
    );

    Optional<SalesInvoice> findBySales_Id(Long salesId);

    Optional<SalesInvoice> findByInvoiceNumber(String invoiceNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM SalesInvoice i WHERE i.id = :id")
    Optional<SalesInvoice> findByIdForUpdate(@Param("id") Long id);
}