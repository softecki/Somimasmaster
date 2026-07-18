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
package com.somimas.saas.web;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.somimas.saas.payment.WebhookService;
import com.somimas.saas.web.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class GatewayWebhookApiResourceTest {

    @Mock
    private WebhookService webhookService;

    @InjectMocks
    private WebhookController webhookController;

    @Test
    void rejectsInvalidSignature() {
        String payload = "{\"event\":\"charge.completed\",\"data\":{\"id\":\"evt-1\",\"tx_ref\":\"tx-1\",\"amount\":\"49\"}}";
        doThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature"))
                .when(webhookService).handleFlutterwaveWebhook("bad-hash", payload);

        assertThrows(ApiException.class,
                () -> webhookController.handleFlutterwaveWebhook("bad-hash", payload));
    }

    @Test
    void duplicateWebhookIsIdempotent() {
        String payload = "{\"event\":\"charge.completed\",\"data\":{\"id\":\"evt-dup\",\"tx_ref\":\"tx-dup\",\"amount\":\"49\"}}";
        webhookController.handleFlutterwaveWebhook("valid-hash", payload);
        webhookController.handleFlutterwaveWebhook("valid-hash", payload);
        verify(webhookService).handleFlutterwaveWebhook("valid-hash", payload);
        verify(webhookService).handleFlutterwaveWebhook("valid-hash", payload);
        verifyNoMoreInteractions(webhookService);
    }
}
