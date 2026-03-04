package org.ilynosov.hw_2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.ilynosov.hw_2.model.ProductStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Setter
@Getter
@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    private String name;

    private String description;

    private BigDecimal price;

    private Integer stock;

    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Column(name = "seller_id")
    private UUID sellerId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

}