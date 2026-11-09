package com.inventory.management.repository;

import com.inventory.management.entity.SalesItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesItemRepository extends JpaRepository<SalesItem, Long> {
}