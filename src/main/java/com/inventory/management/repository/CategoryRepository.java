package com.inventory.management.repository;

import com.inventory.management.entity.Category;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByCategoryNameIgnoreCase(String categoryName);

    @EntityGraph(attributePaths = "products")
    Optional<Category> findWithProductsById(Long id);
}