package org.ilynosov.hw_2.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ilynosov.hw_2.api.ProductsApi;
import org.ilynosov.hw_2.entity.Product;
import org.ilynosov.hw_2.model.*;
import org.ilynosov.hw_2.service.ProductService;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
public class ProductsController implements ProductsApi {

    private final ProductService productService;

    @Override
    public ResponseEntity<ProductResponse> createProduct(@Valid ProductCreate productCreate) {

        var product = productService.createProduct(productCreate);

        return ResponseEntity.status(201).body(productService.toResponse(product));
    }

    @Override
    public ResponseEntity<ProductPage> getProducts(
            Integer page,
            Integer size,
            ProductStatus status,
            String category
    ) {

        PageRequest pageable = PageRequest.of(page, size);

        Page<Product> products = productService.getProducts(pageable, status, category);

        List<ProductResponse> content = products
                .getContent()
                .stream()
                .map(productService::toResponse)
                .toList();

        ProductPage response = new ProductPage()
                .content(content)
                .totalElements((int) products.getTotalElements())
                .page(products.getNumber())
                .size(products.getSize());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ProductResponse> getProductById(UUID id) {

        var product = productService.getById(id);

        return ResponseEntity.ok(productService.toResponse(product));
    }

    @Override
    public ResponseEntity<ProductResponse> updateProduct(UUID id, @Valid ProductUpdate body) {

        var product = productService.update(id, body);

        return ResponseEntity.ok(productService.toResponse(product));
    }

    @Override
    public ResponseEntity<Void> deleteProduct(UUID id) {

        productService.archive(id);

        return ResponseEntity.noContent().build();
    }
}