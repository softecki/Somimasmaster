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

import com.somimas.saas.config.SomimasProperties;
import com.somimas.saas.web.ApiException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FlutterwavePaymentGatewayImpl implements FlutterwavePaymentGateway, PaymentGateway {

    private final SomimasProperties somimasProperties;
    private final WebhookReceiptRepository webhookReceiptRepository;
    private final PaymentService paymentService;

    public FlutterwavePaymentGatewayImpl(SomimasProperties somimasProperties, WebhookReceiptRepository webhookReceiptRepository,
            PaymentService paymentService) {
        this.somimasProperties = somimasProperties;
        this.webhookReceiptRepository = webhookReceiptRepository;
        this.paymentService = paymentService;
    }

    @Override
    public String providerCode() {
        return "FLUTTERWAVE";
    }

    @Override
    public void verifySignature(String verifHash) {
        verifyWebhookSignature(verifHash);
    }

    @Override
    public void verifyWebhookSignature(String signatureHeader) {
        String expected = somimasProperties.getFlutterwave().getSecretHash();
        if (!StringUtils.hasText(expected) || !expected.equals(signatureHeader)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }
    }

    @Override
    @Transactional
    public void handleChargeCompleted(String eventId, String gatewayRef, BigDecimal amount, String rawPayload) {
        handlePaymentSucceeded(eventId, gatewayRef, amount, rawPayload);
    }

    @Override
    @Transactional
    public void handlePaymentSucceeded(String providerEventId, String gatewayRef, BigDecimal amount, String rawPayload) {
        if (webhookReceiptRepository.findByEventId(providerEventId).isPresent()) {
            return;
        }
        WebhookReceipt receipt = new WebhookReceipt();
        receipt.setProvider(providerCode());
        receipt.setEventId(providerEventId);
        receipt.setPayload(rawPayload);
        receipt.setStatus("PROCESSED");
        receipt.setProcessedAt(LocalDateTime.now());
        webhookReceiptRepository.save(receipt);
        paymentService.recordSuccessfulPayment(gatewayRef, amount, rawPayload);
    }
}
