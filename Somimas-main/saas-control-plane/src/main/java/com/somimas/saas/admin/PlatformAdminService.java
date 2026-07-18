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
package com.somimas.saas.admin;

import com.somimas.saas.audit.AuditService;
import com.somimas.saas.organization.OrganizationService;
import com.somimas.saas.payment.BankDeposit;
import com.somimas.saas.payment.BankDepositRepository;
import com.somimas.saas.payment.BankDepositReviewService;
import com.somimas.saas.provisioning.FineractProvisioningClient;
import com.somimas.saas.web.dto.request.BankDepositReviewRequest;
import com.somimas.saas.web.dto.response.BankDepositResponse;
import com.somimas.saas.web.dto.response.OrganizationResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAdminService {

    private final OrganizationService organizationService;
    private final BankDepositRepository bankDepositRepository;
    private final BankDepositReviewService bankDepositReviewService;
    private final FineractProvisioningClient fineractProvisioningClient;
    private final AuditService auditService;

    public PlatformAdminService(OrganizationService organizationService, BankDepositRepository bankDepositRepository,
            BankDepositReviewService bankDepositReviewService, FineractProvisioningClient fineractProvisioningClient,
            AuditService auditService) {
        this.organizationService = organizationService;
        this.bankDepositRepository = bankDepositRepository;
        this.bankDepositReviewService = bankDepositReviewService;
        this.fineractProvisioningClient = fineractProvisioningClient;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> listOrganizations() {
        return organizationService.listAll();
    }

    @Transactional(readOnly = true)
    public List<BankDeposit> listPendingDeposits() {
        return bankDepositRepository.findByStatusOrderBySubmittedAtAsc("PENDING_REVIEW");
    }

    @Transactional
    public BankDepositResponse approveDeposit(Long depositId, Long reviewerIdentityId, BankDepositReviewRequest request) {
        return bankDepositReviewService.review(depositId, reviewerIdentityId, request);
    }

    @Transactional
    public OrganizationResponse suspendOrganization(Long organizationId, Long actorIdentityId) {
        OrganizationResponse response = organizationService.updateStatus(organizationId, "SUSPENDED");
        fineractProvisioningClient.updateAccessState(response.slug(), "SUSPENDED");
        auditService.record(actorIdentityId, organizationId, "organization.suspend", "organization", organizationId, null);
        return response;
    }

    @Transactional
    public OrganizationResponse reactivateOrganization(Long organizationId, Long actorIdentityId) {
        OrganizationResponse response = organizationService.updateStatus(organizationId, "ACTIVE");
        fineractProvisioningClient.updateAccessState(response.slug(), "ACTIVE");
        auditService.record(actorIdentityId, organizationId, "organization.reactivate", "organization", organizationId, null);
        return response;
    }
}
