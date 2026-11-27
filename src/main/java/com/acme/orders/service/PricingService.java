package com.acme.orders.service;

import com.acme.orders.domain.Coupon;
import com.acme.orders.domain.Order;
import com.acme.orders.domain.OrderItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

@Service
public class PricingService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final Clock clock;

    public PricingService(Clock clock) {
        this.clock = clock;
    }

    public void validate(Coupon coupon) {
        if (!coupon.getExpiresAt().isAfter(LocalDate.now(clock))) {
            throw new BusinessException("Coupon " + coupon.getCode() + " is expired");
        }
    }

    /** Whether the coupon can be applied to this order: whole-purchase coupons always, product coupons only if the product is in the order. */
    public boolean appliesTo(Order order, Coupon coupon) {
        return !coupon.isProductScoped() || order.getItems().stream().anyMatch(i -> matches(i, coupon));
    }

    public BigDecimal discountFor(Order order, Coupon coupon) {
        BigDecimal base = coupon.isProductScoped()
                ? order.getItems().stream()
                        .filter(i -> matches(i, coupon))
                        .map(OrderItem::lineTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : order.subtotal();
        BigDecimal rate = BigDecimal.valueOf(coupon.getPercentOff()).divide(ONE_HUNDRED);
        return base.multiply(rate).setScale(2, RoundingMode.HALF_EVEN).min(base);
    }

    private static boolean matches(OrderItem item, Coupon coupon) {
        return Objects.equals(item.getProduct().getId(), coupon.getProduct().getId());
    }
}
