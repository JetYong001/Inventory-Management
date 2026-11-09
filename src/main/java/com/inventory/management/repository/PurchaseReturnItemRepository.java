package com.inventory.management.repository;

import com.inventory.management.entity.PurchaseReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PurchaseReturnItemRepository
        extends JpaRepository<PurchaseReturnItem, Long> {

    List<PurchaseReturnItem> findByPurchaseReturn_Id(Long purchaseReturnId);

    @Query("""
    SELECT COALESCE(SUM(item.quantity), 0)
    FROM PurchaseReturnItem item
    WHERE item.purchaseItem.id = :purchaseItemId
""")
    Long getReturnedQuantity(
            @Param("purchaseItemId") Long purchaseItemId
    );
}