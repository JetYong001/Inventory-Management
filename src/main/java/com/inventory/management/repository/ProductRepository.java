package com.inventory.management.repository;

import com.inventory.management.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByProductCode(String productCode);

    boolean existsByCategory_Id(Long categoryId);

    Page<Product> findAllByActiveTrue(Pageable pageable);

    Page<Product> findAllByActiveFalse(Pageable pageable);

    Optional<Product> findByIdAndActiveTrue(Long id);

    @Query("""
            SELECT p
            FROM Product p
            WHERE (
                :active IS NULL
                OR p.active = :active
            )
            AND (
                LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            """)
    Page<Product> searchProducts(
            @Param("keyword") String keyword,
            @Param("active") Boolean active,
            Pageable pageable
    );

    List<Product> findAllByOrderByProductNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :productId")
    Optional<Product> findByIdForUpdate(
            @Param("productId") Long productId
    );
}