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

import com.somimas.saas.admin.PlatformAdminService;
import com.somimas.saas.payment.BankDeposit;
import com.somimas.saas.web.dto.request.BankDepositReviewRequest;
import com.somimas.saas.web.dto.response.BankDepositResponse;
import com.somimas.saas.web.dto.response.OrganizationResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform")
public class PlatformAdminController {

    private final PlatformAdminService platformAdminService;

    public PlatformAdminController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @GetMapping("/organizations")
    public List<OrganizationResponse> listOrganizations() {
        return platformAdminService.listOrganizations();
    }

    @GetMapping("/bank-deposits/pending")
    public List<BankDeposit> listPendingDeposits() {
        return platformAdminService.listPendingDeposits();
    }

    @PostMapping("/bank-deposits/{depositId}/review")
    public BankDepositResponse reviewDeposit(@PathVariable Long depositId,
            @Valid @RequestBody BankDepositReviewRequest request) {
        return platformAdminService.approveDeposit(depositId, SecurityUtils.currentIdentityId(), request);
    }

    @PostMapping("/organizations/{id}/suspend")
    public OrganizationResponse suspendOrganization(@PathVariable("id") Long organizationId) {
        return platformAdminService.suspendOrganization(organizationId, SecurityUtils.currentIdentityId());
    }

    @PostMapping("/organizations/{id}/reactivate")
    public OrganizationResponse reactivateOrganization(@PathVariable("id") Long organizationId) {
        return platformAdminService.reactivateOrganization(organizationId, SecurityUtils.currentIdentityId());
    }
}
