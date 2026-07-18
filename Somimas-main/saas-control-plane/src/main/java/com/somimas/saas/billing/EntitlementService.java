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

import com.somimas.saas.web.dto.response.EntitlementResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntitlementService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanEntitlementRepository planEntitlementRepository;
    private final EntitlementRepository entitlementRepository;

    public EntitlementService(SubscriptionRepository subscriptionRepository, PlanEntitlementRepository planEntitlementRepository,
            EntitlementRepository entitlementRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planEntitlementRepository = planEntitlementRepository;
        this.entitlementRepository = entitlementRepository;
    }

    @Transactional(readOnly = true)
    public List<EntitlementResponse> getForOrganization(Long organizationId) {
        Subscription subscription = subscriptionRepository.findByOrganizationId(organizationId).orElse(null);
        if (subscription == null) {
            return List.of();
        }
        List<EntitlementResponse> result = new ArrayList<>();
        for (PlanEntitlement planEntitlement : planEntitlementRepository.findByPlanId(subscription.getPlanId())) {
            entitlementRepository.findById(planEntitlement.getEntitlementId()).ifPresent(entitlement -> result.add(
                    new EntitlementResponse(entitlement.getCode(), planEntitlement.getValue())));
        }
        return result;
    }
}
