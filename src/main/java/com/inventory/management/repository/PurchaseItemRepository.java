
package com.inventory.management.repository;

import com.inventory.management.entity.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {

}
