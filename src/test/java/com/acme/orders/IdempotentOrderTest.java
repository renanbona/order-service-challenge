package com.acme.orders;

import com.acme.orders.domain.Order;
import com.acme.orders.service.OrderService;
import com.acme.orders.web.dto.CreateOrderRequest;
import com.acme.orders.web.dto.OrderItemRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotentOrderTest {

    private static final String ORDER_JSON_PRODUCT_1 = """
            {
              "customerId": %d,
              "items": [
                { "productId": 1, "quantity": 1 }
              ]
            }
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    OrderService orderService;

    @Test
    void retryWithSameKeyReturnsSameOrderAndDecrementsStockOnce() throws Exception {
        long customerId = 1001;
        String key = UUID.randomUUID().toString();
        int stockBefore = stockOf(1);

        String first = createWithKey(customerId, key, ORDER_JSON_PRODUCT_1.formatted(customerId));
        String second = createWithKey(customerId, key, ORDER_JSON_PRODUCT_1.formatted(customerId));

        assertThat(second).isEqualTo(first);
        assertThat(stockOf(1)).isEqualTo(stockBefore - 1);
    }

    @Test
    void reusingKeyForDifferentPayloadIsRejected() throws Exception {
        long customerId = 1002;
        String key = UUID.randomUUID().toString();
        createWithKey(customerId, key, ORDER_JSON_PRODUCT_1.formatted(customerId));

        String differentQuantity = """
                {
                  "customerId": %d,
                  "items": [
                    { "productId": 1, "quantity": 2 }
                  ]
                }
                """.formatted(customerId);

        mvc.perform(post("/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(differentQuantity))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void sameKeyForDifferentCustomersCreatesSeparateOrders() throws Exception {
        String key = UUID.randomUUID().toString();

        String orderA = createWithKey(2001, key, ORDER_JSON_PRODUCT_1.formatted(2001));
        String orderB = createWithKey(2002, key, ORDER_JSON_PRODUCT_1.formatted(2002));

        assertThat(orderB).isNotEqualTo(orderA);
    }

    @Test
    void requestsWithoutKeyAreNotDeduplicated() throws Exception {
        long customerId = 1003;
        int stockBefore = stockOf(1);

        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_JSON_PRODUCT_1.formatted(customerId)))
                .andExpect(status().isCreated());
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_JSON_PRODUCT_1.formatted(customerId)))
                .andExpect(status().isCreated());

        assertThat(stockOf(1)).isEqualTo(stockBefore - 2);
    }

    @Test
    void concurrentRequestsWithSameKeyCreateExactlyOneOrder() throws Exception {
        long customerId = 1004;
        String key = UUID.randomUUID().toString();
        int stockBefore = stockOf(1);

        CreateOrderRequest request = new CreateOrderRequest(customerId,
                List.of(new OrderItemRequest(1L, 1)), null);

        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Order>> futures = IntStream.range(0, attempts)
                    .mapToObj(i -> pool.submit((Callable<Order>) () -> {
                        start.await();
                        return orderService.create(request, key);
                    }))
                    .toList();
            start.countDown();

            List<Long> ids = futures.stream().map(f -> {
                try {
                    return f.get().getId();
                } catch (Exception e) {
                    throw new AssertionError("Concurrent attempt failed", e);
                }
            }).toList();

            assertThat(ids).containsOnly(ids.get(0));
        } finally {
            pool.shutdownNow();
        }

        assertThat(stockOf(1)).isEqualTo(stockBefore - 1);
    }

    private String createWithKey(long customerId, String key, String body) throws Exception {
        return mvc.perform(post("/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andReturn().getResponse().getHeader("Location");
    }

    private int stockOf(long productId) throws Exception {
        String json = mvc.perform(get("/products/" + productId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.stock");
    }
}
