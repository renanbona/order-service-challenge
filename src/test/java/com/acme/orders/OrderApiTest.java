package com.acme.orders;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
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
    void cancellingOrderReturnsStock() throws Exception {
        int stockBefore = stockOf(3);

        String body = """
                {
                  "customerId": 11,
                  "items": [
                    { "productId": 3, "quantity": 1 }
                  ]
                }
                """;

        String location = mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        assertThat(stockOf(3)).isEqualTo(stockBefore - 1);

        mvc.perform(post(location + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(stockOf(3)).isEqualTo(stockBefore);
    }

    @Test
    void cancellingAnAlreadyCancelledOrderDoesNotReturnStockTwice() throws Exception {
        int stockBefore = stockOf(3);

        String body = """
                {
                  "customerId": 12,
                  "items": [
                    { "productId": 3, "quantity": 1 }
                  ]
                }
                """;

        String location = mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(post(location + "/cancel")).andExpect(status().isOk());
        mvc.perform(post(location + "/cancel")).andExpect(status().isUnprocessableEntity());

        assertThat(stockOf(3)).isEqualTo(stockBefore);
    }

    private int stockOf(long productId) throws Exception {
        String json = mvc.perform(get("/products/" + productId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.stock");
    }

    @Test
    void returns404ForUnknownOrder() throws Exception {
        mvc.perform(get("/orders/999999"))
                .andExpect(status().isNotFound());
    }
}
