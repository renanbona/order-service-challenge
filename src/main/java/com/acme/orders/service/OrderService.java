package com.acme.orders.service;

import com.acme.orders.domain.Coupon;
import com.acme.orders.domain.Order;
import com.acme.orders.domain.OrderItem;
import com.acme.orders.domain.OrderStatus;
import com.acme.orders.domain.Product;
import com.acme.orders.repository.CouponRepository;
import com.acme.orders.repository.OrderRepository;
import com.acme.orders.repository.ProductRepository;
import com.acme.orders.web.dto.CreateOrderRequest;
import com.acme.orders.web.dto.OrderItemRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final PricingService pricingService;
    private final TransactionTemplate transactions;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        CouponRepository couponRepository,
                        PricingService pricingService,
                        PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.couponRepository = couponRepository;
        this.pricingService = pricingService;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Order create(CreateOrderRequest request) {
        return create(request, null);
    }

    /**
     * Creates an order, or returns the order already created for the same {@code idempotencyKey}.
     *
     * <p>Deliberately not {@code @Transactional}: each attempt runs in its own transaction, so that when a
     * concurrent request with the same key wins the unique constraint, this attempt can roll back (including
     * its stock changes) and return the winner's order instead.
     */
    public Order create(CreateOrderRequest request, String idempotencyKey) {
        if (idempotencyKey == null) {
            return transactions.execute(status -> placeOrder(request, null, null));
        }
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
            throw new BusinessException("Idempotency-Key must be between 1 and 255 characters");
        }

        String fingerprint = fingerprint(request);
        Optional<Order> replay = replayOf(request.customerId(), idempotencyKey, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }

        try {
            return transactions.execute(status -> placeOrder(request, idempotencyKey, fingerprint));
        } catch (DataIntegrityViolationException e) {
            // A concurrent request with the same key committed first; its order is the answer.
            return replayOf(request.customerId(), idempotencyKey, fingerprint).orElseThrow(() -> e);
        }
    }

    private Optional<Order> replayOf(Long customerId, String idempotencyKey, String fingerprint) {
        return orderRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                .map(existing -> {
                    if (!fingerprint.equals(existing.getRequestFingerprint())) {
                        throw new BusinessException("Idempotency-Key " + idempotencyKey
                                + " was already used for a different request");
                    }
                    log.info("Idempotency-Key {} replayed; returning order {}", idempotencyKey, existing.getId());
                    return existing;
                });
    }

    private Order placeOrder(CreateOrderRequest request, String idempotencyKey, String fingerprint) {
        Order order = new Order(request.customerId());
        order.setIdempotencyKey(idempotencyKey);
        order.setRequestFingerprint(fingerprint);

        for (OrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId()).orElseThrow();

            if (product.getStock() < itemRequest.quantity()) {
                throw new BusinessException("Insufficient stock for product " + product.getSku());
            }
            product.setStock(product.getStock() - itemRequest.quantity());

            order.addItem(new OrderItem(product, itemRequest.quantity()));
        }

        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            Coupon coupon = couponRepository.findByCode(request.couponCode())
                    .orElseThrow(() -> new NotFoundException("Coupon not found: " + request.couponCode()));
            pricingService.validate(coupon);
            order.setCoupon(coupon);
            order.setDiscount(pricingService.discountFor(order.subtotal(), coupon));
        }

        Order saved = orderRepository.save(order);
        log.info("Order {} created for customer {}", saved.getId(), saved.getCustomerId());
        return saved;
    }

    /** Hash of everything that defines the purchase; a reused key must carry the same fingerprint. */
    static String fingerprint(CreateOrderRequest request) {
        String coupon = request.couponCode() == null || request.couponCode().isBlank()
                ? "" : request.couponCode().trim();
        StringBuilder canonical = new StringBuilder()
                .append("customer=").append(request.customerId())
                .append(";coupon=").append(coupon)
                .append(";items=");
        for (OrderItemRequest item : request.items()) {
            canonical.append(item.productId()).append('x').append(item.quantity()).append(',');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public Order get(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found: " + id));
    }

    public List<Order> listByCustomer(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    public Order cancel(Long id) {
        Order order = get(id);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("Order " + id + " is already cancelled");
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        restoreStock(order);

        log.info("Order {} cancelled", id);
        return order;
    }

    @Transactional
    public void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStock(product.getStock() + item.getQuantity());
        }
    }
}
