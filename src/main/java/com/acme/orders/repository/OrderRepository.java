package com.acme.orders.repository;

import com.acme.orders.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Optional<Order> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);
}
