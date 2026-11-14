package com.inventory.management.repository;

import com.inventory.management.entity.SupplierRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRefundRepository
        extends JpaRepository<SupplierRefund, Long> {

    List<SupplierRefund> findBySupplierIdOrderByCreatedAtDesc(
            Long supplierId
    );

    boolean existsByRefundNumber(String refundNumber);

    Optional<SupplierRefund> findByIdempotencyKey(String idempotencyKey);
}