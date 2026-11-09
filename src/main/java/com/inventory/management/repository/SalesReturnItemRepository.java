package com.inventory.management.repository;

import com.inventory.management.entity.SalesReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalesReturnItemRepository
        extends JpaRepository<SalesReturnItem, Long> {

    List<SalesReturnItem> findBySalesReturn_Id(Long salesReturnId);

    @Query("""
    SELECT COALESCE(SUM(item.quantity), 0)
    FROM SalesReturnItem item
    WHERE item.salesItem.id = :salesItemId
""")
    Long getReturnedQuantity(
            @Param("salesItemId") Long salesItemId
    );
}