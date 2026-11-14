package com.inventory.management.repository;

import com.inventory.management.entity.SalesReturn;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalesReturnRepository
        extends JpaRepository<SalesReturn, Long> {

    boolean existsByReturnNumber(String returnNumber);

    Optional<SalesReturn> findByReturnNumber(String returnNumber);

    Optional<SalesReturn> findByIdempotencyKey(String idempotencyKey);

    List<SalesReturn> findBySales_IdOrderByReturnDateDesc(
            Long salesId
    );
}