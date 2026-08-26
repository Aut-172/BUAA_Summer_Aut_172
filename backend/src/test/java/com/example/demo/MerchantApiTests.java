package com.example.demo;

import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.merchant.entity.Product;
import com.example.demo.merchant.mapper.ProductMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MerchantApiTests extends BackendIntegrationTestSupport {

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private ProductMapper productMapper;

    @Test
    void merchantProfileUpdatePreservesSensitiveMetricsAndRequiresMerchantRole() throws Exception {
        String consumerToken = login("/api/auth/login", "demo", "123456");
        String merchantToken = login("/api/auth/merchant/login", "merchant1", "123456");

        mockMvc.perform(get("/api/merchant/profile")
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问商家接口"));

        mockMvc.perform(put("/api/merchant/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated Campus Kitchen",
                                  "phone": "13800138888",
                                  "address": "Updated Road",
                                  "category": "Food",
                                  "description": "Updated description",
                                  "status": "frozen",
                                  "rating": 1.0,
                                  "monthlySales": 9999,
                                  "deliveryFee": 6.00
                                }
                                """)
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("Updated Campus Kitchen"))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.rating").value(4.5))
                .andExpect(jsonPath("$.data.monthlySales").value(1280))
                .andExpect(jsonPath("$.data.deliveryFee").value(6.00));

        Merchant updated = merchantMapper.selectById(20001L);
        assertEquals("Updated Campus Kitchen", updated.getName());
        assertEquals("active", updated.getStatus());
        assertEquals(new BigDecimal("4.5"), updated.getRating());
        assertEquals(1280, updated.getMonthlySales());
    }

    @Test
    void merchantProductApiAddsUpdatesRejectsCrossStoreMutationAndDeletes() throws Exception {
        String merchantToken = login("/api/auth/merchant/login", "merchant1", "123456");

        String addResponse = mockMvc.perform(post("/api/merchant/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": 1,
                                  "name": "Merchant Test Product",
                                  "price": 18.50,
                                  "stock": 30,
                                  "description": "Created by merchant test"
                                }
                                """)
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.merchantId").value("20001"))
                .andExpect(jsonPath("$.data.monthlySales").value(0))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode addedProduct = readBody(addResponse).path("data");
        long productId = addedProduct.path("id").asLong();

        mockMvc.perform(put("/api/merchant/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "%s",
                                  "name": "Merchant Test Product Updated",
                                  "price": 20.00,
                                  "stock": 25,
                                  "monthlySales": 9999
                                }
                                """.formatted(productId))
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("Merchant Test Product Updated"))
                .andExpect(jsonPath("$.data.price").value(20.00))
                .andExpect(jsonPath("$.data.stock").value(25))
                .andExpect(jsonPath("$.data.monthlySales").value(0));

        Merchant otherMerchant = new Merchant();
        otherMerchant.setId(25001L);
        otherMerchant.setUsername("otherProductMerchant");
        otherMerchant.setPassword("secret");
        otherMerchant.setName("Other Product Merchant");
        otherMerchant.setPhone("13800138701");
        otherMerchant.setAddress("Other Road");
        otherMerchant.setCategory("Food");
        otherMerchant.setStatus("active");
        otherMerchant.setRating(new BigDecimal("4.0"));
        otherMerchant.setMonthlySales(0);
        otherMerchant.setDeliveryFee(BigDecimal.ZERO);
        merchantMapper.insert(otherMerchant);

        Product otherProduct = new Product();
        otherProduct.setId(35001L);
        otherProduct.setMerchantId(25001L);
        otherProduct.setCategoryId(1L);
        otherProduct.setName("Other Merchant Product");
        otherProduct.setPrice(new BigDecimal("9.90"));
        otherProduct.setStock(10);
        otherProduct.setStatus("active");
        otherProduct.setType("delivery");
        productMapper.insert(otherProduct);

        mockMvc.perform(put("/api/merchant/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 35001,
                                  "name": "Illegal Update"
                                }
                                """)
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该商品"));

        mockMvc.perform(delete("/api/merchant/products/{productId}", productId)
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertNull(productMapper.selectById(productId));
        assertEquals("Other Merchant Product", productMapper.selectById(35001L).getName());
    }
}
