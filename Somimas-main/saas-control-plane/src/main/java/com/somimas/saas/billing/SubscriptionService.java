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

import com.somimas.saas.config.SomimasProperties;
import com.somimas.saas.web.ApiException;
import com.somimas.saas.web.dto.response.SubscriptionResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final SomimasProperties somimasProperties;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, PlanRepository planRepository,
            SomimasProperties somimasProperties) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.somimasProperties = somimasProperties;
    }

    @Transactional
    public Subscription createTrialSubscription(Long organizationId, Long planId) {
        LocalDateTime now = LocalDateTime.now();
        int trialDays = somimasProperties.getTrial().getDays();
        Subscription subscription = new Subscription();
        subscription.setOrganizationId(organizationId);
        subscription.setPlanId(planId);
        subscription.setStatus("TRIAL");
        subscription.setTrialEndsAt(now.plusDays(trialDays));
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(now.plusDays(trialDays));
        subscription.setCreatedAt(now);
        subscription.setUpdatedAt(now);
        return subscriptionRepository.save(subscription);
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getForOrganization(Long organizationId) {
        Subscription subscription = subscriptionRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subscription not found"));
        Plan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Plan not found"));
        return new SubscriptionResponse(subscription.getId(), organizationId, plan.getCode(), plan.getName(),
                subscription.getStatus(), subscription.getTrialEndsAt(), subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd());
    }

    @Transactional
    public int expireTrials() {
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> expired = subscriptionRepository.findByStatusAndTrialEndsAtBefore("TRIAL", now);
        for (Subscription subscription : expired) {
            subscription.setStatus("EXPIRED");
            subscription.setUpdatedAt(now);
            subscriptionRepository.save(subscription);
        }
        return expired.size();
    }
}
