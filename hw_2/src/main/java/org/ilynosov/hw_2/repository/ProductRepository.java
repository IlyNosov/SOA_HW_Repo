package org.ilynosov.hw_2.repository;

import jakarta.persistence.LockModeType;
import org.ilynosov.hw_2.entity.Product;
import org.ilynosov.hw_2.model.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, java.util.UUID> {

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findByCategory(String category, Pageable pageable);

    Page<Product> findByStatusAndCategory(ProductStatus status, String category, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") UUID id);
}