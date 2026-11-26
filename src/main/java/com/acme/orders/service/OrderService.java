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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final PricingService pricingService;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        CouponRepository couponRepository,
                        PricingService pricingService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.couponRepository = couponRepository;
        this.pricingService = pricingService;
    }

    @Transactional
    public Order create(CreateOrderRequest request) {
        Order order = new Order(request.customerId());

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

    public Order get(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found: " + id));
    }

    public List<Order> listByCustomer(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional
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

    /**
     * Private and called from within {@link #cancel(Long)}: it runs inside cancel's transaction.
     * (A @Transactional here would be ignored, since self-invocation bypasses the Spring proxy.)
     */
    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStock(product.getStock() + item.getQuantity());
        }
    }
}
