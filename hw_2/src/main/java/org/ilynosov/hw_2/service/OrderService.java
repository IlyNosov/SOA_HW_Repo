package org.ilynosov.hw_2.service;

import lombok.RequiredArgsConstructor;
import org.ilynosov.hw_2.entity.*;
import org.ilynosov.hw_2.exception.BusinessException;
import org.ilynosov.hw_2.model.OrderCreateRequest;
import org.ilynosov.hw_2.model.ProductStatus;
import org.ilynosov.hw_2.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final UserOperationRepository userOperationRepository;

    @Value("${app.order.limit-minutes}")
    private int orderLimitMinutes;

    @Transactional
    public Order createOrder(UUID userId, OrderCreateRequest request) {

        // Проверка частоты заказов
        userOperationRepository
                .findFirstByUserIdAndOperationTypeOrderByCreatedAtDesc(
                        userId,
                        OperationType.CREATE_ORDER
                )
                .ifPresent(op -> {
                    if (Duration.between(op.getCreatedAt(), Instant.now())
                            .toMinutes() < orderLimitMinutes) {
                        throw new BusinessException(
                                "ORDER_LIMIT_EXCEEDED",
                                "The order creation or update frequency limit has been exceeded",
                                HttpStatus.TOO_MANY_REQUESTS
                        );
                    }
                });

        // Проверка активного заказа
        orderRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
                        userId,
                        List.of(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING)
                )
                .ifPresent(o -> {
                    throw new BusinessException(
                            "ORDER_HAS_ACTIVE",
                            "The user already has an active order",
                            HttpStatus.CONFLICT
                    );
                });


        Map<UUID, Integer> quantityMap = new HashMap<>();

        for (var item : request.getItems()) {
            quantityMap.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        Map<UUID, Product> productMap = new HashMap<>();

        for (UUID productId : quantityMap.keySet()) {

            Product product = productRepository
                    .findByIdForUpdate(productId)
                    .orElseThrow(() -> new BusinessException(
                            "PRODUCT_NOT_FOUND",
                            "Product not found",
                            HttpStatus.NOT_FOUND
                    ));

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException(
                        "PRODUCT_INACTIVE",
                        "Product inactive",
                        HttpStatus.CONFLICT
                );
            }

            productMap.put(productId, product);
        }

        List<Map<String, Object>> shortages = new ArrayList<>();

        for (var entry : quantityMap.entrySet()) {

            Product product = productMap.get(entry.getKey());
            Integer requested = entry.getValue();

            if (product.getStock() < requested) {

                Map<String, Object> s = new HashMap<>();
                s.put("product_id", product.getId());
                s.put("requested", requested);
                s.put("available", product.getStock());

                shortages.add(s);
            }
        }

        if (!shortages.isEmpty()) {

            Map<String, Object> details = new HashMap<>();
            details.put("shortages", shortages);

            throw new BusinessException(
                    "INSUFFICIENT_STOCK",
                    "Insufficient stock",
                    HttpStatus.CONFLICT,
                    details
            );
        }

        for (var entry : quantityMap.entrySet()) {

            Product product = productMap.get(entry.getKey());
            product.setStock(product.getStock() - entry.getValue());
        }

        productRepository.saveAll(productMap.values());

        // Создание заказа
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(userId);
        order.setStatus(OrderStatus.CREATED);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (var entry : quantityMap.entrySet()) {

            Product product = productMap.get(entry.getKey());
            Integer quantity = entry.getValue();

            OrderItem oi = new OrderItem();
            oi.setId(UUID.randomUUID());
            oi.setOrderId(order.getId());
            oi.setProductId(product.getId());
            oi.setQuantity(quantity);
            oi.setPriceAtOrder(product.getPrice());

            total = total.add(
                    product.getPrice()
                            .multiply(BigDecimal.valueOf(quantity))
            );

            orderItems.add(oi);
        }

        // Промокод
        BigDecimal discount = BigDecimal.ZERO;

        if (request.getPromoCode() != null) {

            PromoCode pc = promoCodeRepository
                    .findByCode(request.getPromoCode())
                    .orElseThrow(() -> new BusinessException(
                            "PROMO_CODE_INVALID",
                            "The promo code is invalid",
                            HttpStatus.UNPROCESSABLE_ENTITY)
                    );

            if (!pc.getActive()
                    || pc.getCurrentUses() >= pc.getMaxUses()
                    || Instant.now().isBefore(pc.getValidFrom())
                    || Instant.now().isAfter(pc.getValidUntil())) {
                throw new BusinessException(
                        "PROMO_CODE_INVALID",
                        "The promo code is invalid",
                        HttpStatus.UNPROCESSABLE_ENTITY
                );
            }

            BigDecimal minAmount = pc.getMinOrderAmount() != null
                    ? pc.getMinOrderAmount()
                    : BigDecimal.ZERO;

            if (total.compareTo(minAmount) < 0) {
                throw new BusinessException(
                        "PROMO_CODE_MIN_AMOUNT",
                        "Order amount is below minimum for promo code",
                        HttpStatus.UNPROCESSABLE_ENTITY
                );
            }

            discount = getBigDecimal(total, pc);

            pc.setCurrentUses(pc.getCurrentUses() + 1);
            promoCodeRepository.save(pc);

            order.setPromoCodeId(pc.getId());
        }

        // Финальная цена
        order.setDiscountAmount(discount);
        order.setTotalAmount(total.subtract(discount));

        // Сохранение
        orderRepository.save(order);
        orderItemRepository.saveAll(orderItems);

        // Запись операции
        UserOperation op = new UserOperation();
        op.setId(UUID.randomUUID());
        op.setUserId(userId);
        op.setOperationType(OperationType.CREATE_ORDER);
        op.setCreatedAt(Instant.now());

        userOperationRepository.save(op);

        return order;
    }

    @Transactional
    public Order cancelOrder(UUID orderId, UUID userId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(
                        "ORDER_NOT_FOUND",
                        "Order not found",
                        HttpStatus.NOT_FOUND
                ));

        // ownership check
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(
                    "ORDER_OWNERSHIP_VIOLATION",
                    "The order belongs to another user",
                    HttpStatus.FORBIDDEN
            );
        }

        // можно отменить только CREATED / PAYMENT_PENDING
        if (order.getStatus() != OrderStatus.CREATED &&
                order.getStatus() != OrderStatus.PAYMENT_PENDING) {

            throw new BusinessException(
                    "INVALID_STATE_TRANSITION",
                    "You cannot cancel an order in its current status.",
                    HttpStatus.CONFLICT
            );
        }

        // вернуть stock
        for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {

            Product product = productRepository.findByIdForUpdate(item.getProductId())
                    .orElseThrow(() -> new BusinessException(
                            "PRODUCT_NOT_FOUND",
                            "Product not found",
                            HttpStatus.NOT_FOUND
                    ));

            product.setStock(product.getStock() + item.getQuantity());
        }

        if (order.getPromoCodeId() != null) {

            PromoCode promo = promoCodeRepository
                    .findById(order.getPromoCodeId())
                    .orElseThrow();

            promo.setCurrentUses(Math.max(0, promo.getCurrentUses() - 1));

            promoCodeRepository.save(promo);
        }

        order.setStatus(OrderStatus.CANCELED);

        return orderRepository.save(order);
    }

    @Transactional
    public Order updateOrder(UUID orderId, UUID userId, OrderCreateRequest request) {

        userOperationRepository
                .findTopByUserIdAndOperationTypeOrderByCreatedAtDesc(userId, OperationType.UPDATE_ORDER)
                .ifPresent(op -> {
                    Duration diff = Duration.between(op.getCreatedAt(), OffsetDateTime.now());

                    if (diff.toMinutes() < orderLimitMinutes) {
                        throw new BusinessException(
                                "ORDER_LIMIT_EXCEEDED",
                                "Order update rate limit exceeded",
                                HttpStatus.TOO_MANY_REQUESTS
                        );
                    }
                });

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(
                        "ORDER_NOT_FOUND",
                        "Order not found",
                        HttpStatus.NOT_FOUND
                ));

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(
                    "ORDER_OWNERSHIP_VIOLATION",
                    "The order belongs to another user",
                    HttpStatus.FORBIDDEN
            );
        }

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessException(
                    "INVALID_STATE_TRANSITION",
                    "You can only update the CREATED order",
                    HttpStatus.CONFLICT
            );
        }

        // вернуть старые остатки
        List<OrderItem> existingItems = orderItemRepository.findByOrderId(orderId);

        for (OrderItem item : existingItems) {
            Product product = productRepository.findByIdForUpdate(item.getProductId())
                    .orElseThrow();

            product.setStock(product.getStock() + item.getQuantity());
        }

        // удалить старые позиции
        orderItemRepository.deleteAll(existingItems);

        BigDecimal total = BigDecimal.ZERO;

        List<Map<String, Object>> shortages = new ArrayList<>();

        for (var item : request.getItems()) {

            Product product = productRepository.findByIdForUpdate(item.getProductId())
                    .orElseThrow(() -> new BusinessException(
                            "PRODUCT_NOT_FOUND",
                            "Product not found",
                            HttpStatus.NOT_FOUND
                    ));

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException(
                        "PRODUCT_INACTIVE",
                        "Product inactive",
                        HttpStatus.CONFLICT
                );
            }

            Integer quantity = item.getQuantity();

            if (product.getStock() < quantity) {
                Map<String, Object> this_item = new HashMap<>();
                this_item.put("product_id", product.getId());
                this_item.put("requested", quantity);
                this_item.put("available", product.getStock());

                shortages.add(this_item);
            }

            product.setStock(product.getStock() - quantity);

            OrderItem orderItem = new OrderItem();
            orderItem.setId(UUID.randomUUID());
            orderItem.setOrderId(orderId);
            orderItem.setProductId(product.getId());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setPriceAtOrder(product.getPrice());

            orderItemRepository.save(orderItem);

            total = total.add(product.getPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        if (!shortages.isEmpty()) {

            Map<String, Object> details = new HashMap<>();
            details.put("shortages", shortages);

            throw new BusinessException(
                    "INSUFFICIENT_STOCK",
                    "Insufficient stock for some products",
                    HttpStatus.CONFLICT,
                    details
            );
        }

        BigDecimal discount = BigDecimal.ZERO;

        if (order.getPromoCodeId() != null) {

            PromoCode promo = promoCodeRepository.findById(order.getPromoCodeId())
                    .orElse(null);

            boolean promoValid = true;

            if (promo == null) {
                promoValid = false;
            } else {

                Instant now = Instant.now();

                if (!Boolean.TRUE.equals(promo.getActive())) promoValid = false;
                if (promo.getCurrentUses() >= promo.getMaxUses()) promoValid = false;
                if (now.isBefore(promo.getValidFrom())) promoValid = false;
                if (now.isAfter(promo.getValidUntil())) promoValid = false;
                if (total.compareTo(promo.getMinOrderAmount()) < 0) promoValid = false;
            }

            if (!promoValid) {

                if (promo != null) {
                    promo.setCurrentUses(Math.max(0, promo.getCurrentUses() - 1));
                    promoCodeRepository.save(promo);
                }

                order.setPromoCodeId(null);
                order.setDiscountAmount(BigDecimal.ZERO);

            } else {

                discount = getBigDecimal(total, promo);

                order.setDiscountAmount(discount);
            }
        }

        order.setTotalAmount(total.subtract(discount));

        UserOperation op = new UserOperation();
        op.setId(UUID.randomUUID());
        op.setUserId(userId);
        op.setOperationType(OperationType.UPDATE_ORDER);
        op.setCreatedAt(OffsetDateTime.now().toInstant());

        userOperationRepository.save(op);

        return orderRepository.save(order);
    }

    private BigDecimal getBigDecimal(BigDecimal total, PromoCode promo) {
        BigDecimal discount;
        if (promo.getDiscountType() == DiscountType.PERCENTAGE) {

            discount = total
                    .multiply(promo.getDiscountValue())
                    .divide(BigDecimal.valueOf(100));

            BigDecimal maxDiscount = total.multiply(BigDecimal.valueOf(0.7));

            if (discount.compareTo(maxDiscount) > 0) {
                discount = maxDiscount;
            }

        } else {
            discount = promo.getDiscountValue().min(total);
        }
        return discount;
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
}