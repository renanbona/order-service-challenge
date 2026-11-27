package com.acme.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Locale;

@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    /** Discount percentage, from 1 to 100. */
    @Column(name = "percent_off", nullable = false)
    private int percentOff;

    @Column(name = "expires_at", nullable = false)
    private LocalDate expiresAt;

    /** Product the discount is restricted to; null means the discount applies to the whole purchase. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    protected Coupon() {
    }

    public Coupon(String code, int percentOff, LocalDate expiresAt) {
        this(code, percentOff, expiresAt, null);
    }

    public Coupon(String code, int percentOff, LocalDate expiresAt, Product product) {
        this.code = code;
        this.percentOff = percentOff;
        this.expiresAt = expiresAt;
        this.product = product;
    }

    /** Codes are stored and matched in upper case, without surrounding spaces. */
    public static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isProductScoped() {
        return product != null;
    }

    public Product getProduct() {
        return product;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public int getPercentOff() {
        return percentOff;
    }

    public LocalDate getExpiresAt() {
        return expiresAt;
    }
}
