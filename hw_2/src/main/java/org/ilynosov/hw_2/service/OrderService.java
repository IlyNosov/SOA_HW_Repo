package org.ilynosov.hw_2.service;

import lombok.RequiredArgsConstructor;
import org.ilynosov.hw_2.entity.*;
import org.ilynosov.hw_2.exception.BusinessException;
import org.ilynosov.hw_2.model.OrderCreateRequest;
import org.ilynosov.hw_2.model.ProductStatus;
import org.ilynosov.hw_2.repository.*;
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

    private static final int ORDER_LIMIT_MINUTES = 1;

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
                            .toMinutes() < ORDER_LIMIT_MINUTES) {
                        throw new BusinessException(
                                "ORDER_LIMIT_EXCEEDED",
                                "Превышен лимит частоты создания или обновления заказа",
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
                            "У пользователя уже есть активный заказ",
                            HttpStatus.CONFLICT
                    );
                });

        // Проверка товаров + stock
        Map<Product, Integer> productMap = new HashMap<>();

        for (var item : request.getItems()) {

            Product product = productRepository
                    .findByIdForUpdate(item.getProductId())
                    .orElseThrow(() -> new BusinessException(
                    "PRODUCT_NOT_FOUND",
                    "Товар не найден",
                    HttpStatus.NOT_FOUND)
            );

            // status у тебя String в entity
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException(
                        "PRODUCT_INACTIVE",
                        "Товар неактивен",
                        HttpStatus.CONFLICT
                );
            }

            if (product.getStock() < item.getQuantity()) {
                throw new BusinessException(
                        "INSUFFICIENT_STOCK",
                        "Недостаточно товара на складе",
                        HttpStatus.CONFLICT
                );
            }

            productMap.put(product, item.getQuantity());
        }

        // Резервирование stock
        productMap.forEach((product, qty) ->
                product.setStock(product.getStock() - qty)
        );

        productRepository.saveAll(productMap.keySet());

        // Создание заказа
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(userId);
        order.setStatus(OrderStatus.CREATED);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (var entry : productMap.entrySet()) {

            Product product = entry.getKey();
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
                            "Промокод недействителен",
                            HttpStatus.UNPROCESSABLE_ENTITY)
                    );

            if (!pc.getActive()
                    || pc.getCurrentUses() >= pc.getMaxUses()
                    || Instant.now().isBefore(pc.getValidFrom())
                    || Instant.now().isAfter(pc.getValidUntil())) {
                throw new BusinessException(
                        "PROMO_CODE_INVALID",
                        "Промокод недействителен",
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

            if (pc.getDiscountType() == DiscountType.PERCENTAGE) {

                discount = total
                        .multiply(pc.getDiscountValue())
                        .divide(BigDecimal.valueOf(100));

                BigDecimal maxDiscount = total.multiply(BigDecimal.valueOf(0.7));

                if (discount.compareTo(maxDiscount) > 0) {
                    discount = maxDiscount;
                }

            } else {

                discount = pc.getDiscountValue().min(total);
            }

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
                        "Заказ не найден",
                        HttpStatus.NOT_FOUND
                ));

        // ownership check
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(
                    "ORDER_OWNERSHIP_VIOLATION",
                    "Заказ принадлежит другому пользователю",
                    HttpStatus.FORBIDDEN
            );
        }

        // можно отменить только CREATED / PAYMENT_PENDING
        if (order.getStatus() != OrderStatus.CREATED &&
                order.getStatus() != OrderStatus.PAYMENT_PENDING) {

            throw new BusinessException(
                    "INVALID_STATE_TRANSITION",
                    "Нельзя отменить заказ в текущем статусе",
                    HttpStatus.CONFLICT
            );
        }

        // вернуть stock
        for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {

            Product product = productRepository.findByIdForUpdate(item.getProductId())
                    .orElseThrow(() -> new BusinessException(
                            "PRODUCT_NOT_FOUND",
                            "Товар не найден",
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

                    if (diff.toMinutes() < ORDER_LIMIT_MINUTES) {
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
                        "Заказ не найден",
                        HttpStatus.NOT_FOUND
                ));

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(
                    "ORDER_OWNERSHIP_VIOLATION",
                    "Заказ принадлежит другому пользователю",
                    HttpStatus.FORBIDDEN
            );
        }

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessException(
                    "INVALID_STATE_TRANSITION",
                    "Можно обновлять только CREATED заказ",
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
                            "Товар не найден",
                            HttpStatus.NOT_FOUND
                    ));

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException(
                        "PRODUCT_INACTIVE",
                        "Товар не активен",
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

        order.setTotalAmount(total);

        if (order.getPromoCodeId() != null) {

            PromoCode promo = promoCodeRepository
                    .findById(order.getPromoCodeId())
                    .orElseThrow();

            BigDecimal minAmount = promo.getMinOrderAmount() != null
                    ? promo.getMinOrderAmount()
                    : BigDecimal.ZERO;

            // если заказ больше не подходит под промокод
            if (total.compareTo(minAmount) < 0) {

                promo.setCurrentUses(Math.max(0, promo.getCurrentUses() - 1));
                promoCodeRepository.save(promo);

                order.setPromoCodeId(null);
                order.setDiscountAmount(BigDecimal.ZERO);
            }
        }

        UserOperation op = new UserOperation();
        op.setUserId(userId);
        op.setOperationType(OperationType.UPDATE_ORDER);
        op.setCreatedAt(OffsetDateTime.now().toInstant());

        userOperationRepository.save(op);

        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
}