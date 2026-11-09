package com.inventory.management.repository;

import com.inventory.management.entity.PurchaseReturn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseReturnRepository
        extends JpaRepository<PurchaseReturn, Long> {

    boolean existsByReturnNumber(String returnNumber);

    Optional<PurchaseReturn> findByReturnNumber(String returnNumber);

    List<PurchaseReturn> findByPurchase_IdOrderByReturnDateDesc(Long purchaseId);
}