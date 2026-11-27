package com.acme.orders;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void createsAndFetchesOrder() throws Exception {
        String body = """
                {
                  "customerId": 42,
                  "items": [
                    { "productId": 2, "quantity": 3 }
                  ]
                }
                """;

        String location = mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.subtotal").value(450.00))
                .andExpect(jsonPath("$.total").value(450.00))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(42))
                .andExpect(jsonPath("$.items[0].sku").value("MS-002"));
    }

    @Test
    void rejectsOrderWhenStockIsInsufficient() throws Exception {
        String body = """
                {
                  "customerId": 7,
                  "items": [
                    { "productId": 1, "quantity": 999 }
                  ]
                }
                """;

        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void returns404ForUnknownOrder() throws Exception {
        mvc.perform(get("/orders/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void productCouponDiscountsOnlyThatProductsLines() throws Exception {
        // MOUSE10 is 10% off product 2 only: 2 x 150.00 = 300.00 -> 30.00 off, notebook line not discounted.
        String body = """
                {
                  "customerId": 11,
                  "items": [
                    { "productId": 1, "quantity": 1 },
                    { "productId": 2, "quantity": 2 }
                  ],
                  "couponCode": "MOUSE10"
                }
                """;

        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(4800.00))
                .andExpect(jsonPath("$.discount").value(30.00))
                .andExpect(jsonPath("$.total").value(4770.00))
                .andExpect(jsonPath("$.couponCode").value("MOUSE10"));
    }

    @Test
    void productCouponIsRejectedWhenProductIsNotInOrder() throws Exception {
        String body = """
                {
                  "customerId": 11,
                  "items": [
                    { "productId": 1, "quantity": 1 }
                  ],
                  "couponCode": "MOUSE10"
                }
                """;

        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void expiredProductCouponIsRejected() throws Exception {
        String body = """
                {
                  "customerId": 11,
                  "items": [
                    { "productId": 1, "quantity": 1 }
                  ],
                  "couponCode": "NOTEBOOK5OLD"
                }
                """;

        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void wholePurchaseCouponStillDiscountsSubtotal() throws Exception {
        String body = """
                {
                  "customerId": 12,
                  "items": [
                    { "productId": 2, "quantity": 3 }
                  ],
                  "couponCode": "welcome10"
                }
                """;

        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discount").value(45.00))
                .andExpect(jsonPath("$.total").value(405.00));
    }
}
