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

import com.somimas.saas.web.dto.response.PlanPriceResponse;
import com.somimas.saas.web.dto.response.PlanResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final PlanPriceRepository planPriceRepository;

    public PlanService(PlanRepository planRepository, PlanPriceRepository planPriceRepository) {
        this.planRepository = planRepository;
        this.planPriceRepository = planPriceRepository;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listActivePlans() {
        return planRepository.findAll().stream()
                .filter(plan -> "ACTIVE".equals(plan.getStatus()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Plan getByCode(String code) {
        return planRepository.findByCode(code).orElse(null);
    }

    private PlanResponse toResponse(Plan plan) {
        List<PlanPriceResponse> prices = planPriceRepository.findByPlanIdAndActiveTrue(plan.getId()).stream()
                .map(price -> new PlanPriceResponse(price.getCurrency(), price.getAmount(), price.getBillingInterval()))
                .toList();
        return new PlanResponse(plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(), prices);
    }
}
