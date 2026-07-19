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
package com.somimas.saas.provisioning;

import com.somimas.saas.organization.Organization;
import com.somimas.saas.organization.OrganizationRepository;
import com.somimas.saas.provisioning.FineractProvisioningClient.BridgeAdminSeedResult;
import com.somimas.saas.provisioning.FineractProvisioningClient.BridgeProvisioningResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProvisioningWorker {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningWorker.class);

    private final ProvisioningJobRepository jobRepository;
    private final ProvisioningStepRepository stepRepository;
    private final ProvisioningService provisioningService;
    private final OrganizationRepository organizationRepository;
    private final ProvisioningCredentialRepository provisioningCredentialRepository;
    private final FineractProvisioningClient fineractProvisioningClient;

    public ProvisioningWorker(ProvisioningJobRepository jobRepository, ProvisioningStepRepository stepRepository,
            ProvisioningService provisioningService, OrganizationRepository organizationRepository,
            ProvisioningCredentialRepository provisioningCredentialRepository,
            FineractProvisioningClient fineractProvisioningClient) {
        this.jobRepository = jobRepository;
        this.stepRepository = stepRepository;
        this.provisioningService = provisioningService;
        this.organizationRepository = organizationRepository;
        this.provisioningCredentialRepository = provisioningCredentialRepository;
        this.fineractProvisioningClient = fineractProvisioningClient;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processNextJob() {
        ProvisioningJob job = jobRepository.findFirstByStatusOrderByCreatedAtAsc("PENDING")
                .or(() -> jobRepository.findFirstByStatusOrderByCreatedAtAsc("RUNNING"))
                .orElse(null);
        if (job == null) {
            return;
        }
        if ("PENDING".equals(job.getStatus())) {
            provisioningService.markJobRunning(job);
        }
        Organization organization = organizationRepository.findById(job.getOrganizationId()).orElse(null);
        if (organization == null) {
            provisioningService.markJobFailed(job, "Organization not found");
            return;
        }
        List<ProvisioningStep> steps = stepRepository.findByJobIdOrderByIdAsc(job.getId());
        for (ProvisioningStep step : steps) {
            if ("SUCCEEDED".equals(step.getStatus())) {
                continue;
            }
            if ("FAILED".equals(step.getStatus())) {
                provisioningService.markJobFailed(job, step.getMessage());
                return;
            }
            try {
                executeStep(step, organization);
            } catch (Exception ex) {
                log.error("Provisioning step {} failed for org {}", step.getStepName(), organization.getSlug(), ex);
                provisioningService.failStep(step, safeMessage(ex));
                provisioningService.markJobFailed(job, safeMessage(ex));
                return;
            }
            if ("FAILED".equals(step.getStatus())) {
                provisioningService.markJobFailed(job, step.getMessage());
                return;
            }
        }
        provisioningService.markJobCompleted(job);
    }

    private void executeStep(ProvisioningStep step, Organization organization) {
        provisioningService.startStep(step);
        switch (step.getStepName()) {
            case "RESERVE" -> provisioningService.succeedStep(step, "Slug reserved");
            case "CREATE_SCHEMA" -> {
                BridgeProvisioningResult result = fineractProvisioningClient.createTenant(organization.getSlug(),
                        organization.getName());
                if (result != null && ("CREATED".equals(result.status()) || "EXISTS".equals(result.status()))) {
                    provisioningService.succeedStep(step, result.message());
                } else {
                    provisioningService.failStep(step, result == null ? "No response from bridge" : result.message());
                }
            }
            case "REGISTER" -> {
                BridgeProvisioningResult result = fineractProvisioningClient.getTenantStatus(organization.getSlug());
                if (result != null && "EXISTS".equals(result.status())) {
                    provisioningService.succeedStep(step, "Tenant registered in Fineract");
                } else {
                    provisioningService.failStep(step, result == null ? "Tenant registration check failed" : result.message());
                }
            }
            case "MIGRATE" -> provisioningService.succeedStep(step, "Tenant schema migrated by provisioning bridge");
            case "SEED_ADMIN" -> seedAdmin(step, organization);
            case "VERIFY" -> {
                BridgeProvisioningResult result = fineractProvisioningClient.getTenantStatus(organization.getSlug());
                if (result != null && "EXISTS".equals(result.status())) {
                    provisioningService.succeedStep(step, result.message());
                } else {
                    provisioningService.failStep(step, result == null ? "Verification failed" : result.message());
                }
            }
            case "COMPLETE" -> provisioningService.succeedStep(step, "Provisioning complete");
            default -> provisioningService.failStep(step, "Unknown step: " + step.getStepName());
        }
    }

    private void seedAdmin(ProvisioningStep step, Organization organization) {
        ProvisioningCredential credential = provisioningCredentialRepository.findByOrganizationId(organization.getId())
                .orElse(null);
        if (credential == null) {
            // Idempotent retry after a previous successful seed that already cleaned up the credential.
            provisioningService.succeedStep(step, "Admin already seeded");
            return;
        }
        BridgeAdminSeedResult result = fineractProvisioningClient.seedAdmin(organization.getSlug(), credential.getAdminUsername(),
                credential.getAdminEmail(), credential.getFirstName(), credential.getLastName(), credential.getPasswordHash());
        if (result != null && ("CREATED".equals(result.status()) || "EXISTS".equals(result.status()))) {
            provisioningCredentialRepository.deleteByOrganizationId(organization.getId());
            provisioningService.succeedStep(step, "Owner administrator ready");
        } else {
            provisioningService.failStep(step, result == null ? "Admin seeding failed" : result.message());
        }
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null) {
            return "Provisioning step failed";
        }
        // Never leak password material into step messages.
        return message.replaceAll("(?i)password[^\\s]*", "[redacted]");
    }
}
