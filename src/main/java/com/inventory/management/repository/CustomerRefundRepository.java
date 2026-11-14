package com.inventory.management.repository;

import com.inventory.management.entity.CustomerRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRefundRepository
        extends JpaRepository<CustomerRefund, Long> {

    List<CustomerRefund> findByCustomerIdOrderByCreatedAtDesc(
            Long customerId
    );

    boolean existsByRefundNumber(String refundNumber);

    Optional<CustomerRefund> findByIdempotencyKey(String idempotencyKey);
}