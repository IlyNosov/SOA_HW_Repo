package org.ilynosov.hw_2.service;

import lombok.RequiredArgsConstructor;
import org.ilynosov.hw_2.entity.Product;
import org.ilynosov.hw_2.exception.ProductNotFoundException;
import org.ilynosov.hw_2.model.ProductCreate;
import org.ilynosov.hw_2.model.ProductResponse;
import org.ilynosov.hw_2.model.ProductStatus;
import org.ilynosov.hw_2.model.ProductUpdate;
import org.ilynosov.hw_2.repository.ProductRepository;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repository;

    public Product createProduct(ProductCreate request) {

        Product product = new Product();

        product.setId(UUID.randomUUID());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setStatus(request.getStatus());
        product.setStock(request.getStock());

        product = repository.save(product);
        return repository.findById(product.getId()).orElseThrow();
    }

    public Product getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id.toString()));
    }

    public Page<Product> getProducts(Pageable pageable, ProductStatus status, String category) {

        if (status != null && category != null) {
            return repository.findByStatusAndCategory(status, category, pageable);
        }

        if (status != null) {
            return repository.findByStatus(status, pageable);
        }

        if (category != null) {
            return repository.findByCategory(category, pageable);
        }

        return repository.findAll(pageable);
    }

    public Product update(UUID id, ProductUpdate request) {

        Product product = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id.toString()));

        if (request.getName() != null)
            product.setName(request.getName());

        if (request.getDescription() != null)
            product.setDescription(request.getDescription());

        if (request.getPrice() != null)
            product.setPrice(request.getPrice());

        if (request.getStock() != null)
            product.setStock(request.getStock());

        if (request.getCategory() != null)
            product.setCategory(request.getCategory());

        if (request.getStatus() != null)
            product.setStatus(request.getStatus());

        product.setUpdatedAt(OffsetDateTime.now());
        return repository.save(product);
    }

    public void archive(UUID id) {

        Product product = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id.toString()));

        product.setStatus(ProductStatus.ARCHIVED);
        product.setUpdatedAt(OffsetDateTime.now());

        repository.save(product);
    }

    public ProductResponse toResponse(Product product) {

        ProductResponse response = new ProductResponse();

        response.setId(product.getId());
        response.setName(product.getName());
        response.setDescription(JsonNullable.of(product.getDescription()));
        response.setPrice(product.getPrice());
        response.setCategory(product.getCategory());
        response.setStatus(product.getStatus());
        response.setStock(product.getStock());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());

        return response;
    }
}