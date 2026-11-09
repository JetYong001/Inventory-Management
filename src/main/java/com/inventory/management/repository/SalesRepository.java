package com.inventory.management.repository;

import com.inventory.management.entity.Sales;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SalesRepository extends JpaRepository<Sales, Long> {

    boolean existsBySalesNumber(String salesNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sales s WHERE s.id = :id")
    Optional<Sales> findByIdForUpdate(@Param("id") Long id);
}