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
package com.somimas.saas.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.somimas.saas.config.SomimasProperties;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PlanRepository planRepository;

    private SomimasProperties somimasProperties;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        somimasProperties = new SomimasProperties();
        somimasProperties.getTrial().setDays(21);
        subscriptionService = new SubscriptionService(subscriptionRepository, planRepository, somimasProperties);
    }

    @Test
    void createTrialSubscriptionUsesConfiguredTrialDays() {
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Subscription subscription = subscriptionService.createTrialSubscription(1L, 2L);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription saved = captor.getValue();
        assertEquals("TRIAL", saved.getStatus());
        assertEquals(LocalDateTime.now().plusDays(21).toLocalDate(), saved.getTrialEndsAt().toLocalDate());
        assertEquals(1L, subscription.getOrganizationId());
    }

    @Test
    void expireTrialsMarksSubscriptionsExpired() {
        Subscription trial = new Subscription();
        trial.setId(1L);
        trial.setStatus("TRIAL");
        when(subscriptionRepository.findByStatusAndTrialEndsAtBefore(any(), any())).thenReturn(List.of(trial));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int expired = subscriptionService.expireTrials();

        assertEquals(1, expired);
        assertEquals("EXPIRED", trial.getStatus());
    }
}
