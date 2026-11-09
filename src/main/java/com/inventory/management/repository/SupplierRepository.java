package com.inventory.management.repository;

import com.inventory.management.entity.Supplier;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    boolean existsBySupplierCode(String supplierCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Supplier s WHERE s.id = :id")
    Optional<Supplier> findByIdForUpdate(@Param("id") Long id);
}