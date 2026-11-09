
package com.inventory.management.repository;

import com.inventory.management.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository
        extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId);
}
