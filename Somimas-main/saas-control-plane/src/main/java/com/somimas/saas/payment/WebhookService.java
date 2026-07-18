/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.somimas.saas.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.somimas.saas.web.ApiException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WebhookService {

    private final FlutterwavePaymentGateway flutterwavePaymentGateway;
    private final ObjectMapper objectMapper;

    public WebhookService(FlutterwavePaymentGateway flutterwavePaymentGateway, ObjectMapper objectMapper) {
        this.flutterwavePaymentGateway = flutterwavePaymentGateway;
        this.objectMapper = objectMapper;
    }

    public void handleFlutterwaveWebhook(String verifHash, String payload) {
        flutterwavePaymentGateway.verifySignature(verifHash);
        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText();
            if (!"charge.completed".equals(event)) {
                return;
            }
            JsonNode data = root.path("data");
            String eventId = data.path("id").asText();
            String txRef = data.path("tx_ref").asText();
            BigDecimal amount = new BigDecimal(data.path("amount").asText("0"));
            flutterwavePaymentGateway.handleChargeCompleted(eventId, txRef, amount, payload);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid webhook payload");
        }
    }
}
