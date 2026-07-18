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
import com.somimas.saas.web.ApiException;
import com.somimas.saas.web.dto.response.ProvisioningStatusResponse;
import com.somimas.saas.web.dto.response.ProvisioningStepResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProvisioningService {

    public static final List<String> STEP_ORDER = List.of(
            "RESERVE", "CREATE_SCHEMA", "REGISTER", "MIGRATE", "SEED_ADMIN", "VERIFY", "COMPLETE");

    private final ProvisioningJobRepository jobRepository;
    private final ProvisioningStepRepository stepRepository;
    private final OrganizationRepository organizationRepository;

    public ProvisioningService(ProvisioningJobRepository jobRepository, ProvisioningStepRepository stepRepository,
            OrganizationRepository organizationRepository) {
        this.jobRepository = jobRepository;
        this.stepRepository = stepRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public Long createPendingJob(Long organizationId) {
        LocalDateTime now = LocalDateTime.now();
        ProvisioningJob job = new ProvisioningJob();
        job.setOrganizationId(organizationId);
        job.setStatus("PENDING");
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        job = jobRepository.save(job);

        for (String stepName : STEP_ORDER) {
            ProvisioningStep step = new ProvisioningStep();
            step.setJobId(job.getId());
            step.setStepName(stepName);
            step.setStatus("PENDING");
            stepRepository.save(step);
        }
        return job.getId();
    }

    @Transactional(readOnly = true)
    public ProvisioningStatusResponse getStatusForOrganization(Long organizationId) {
        ProvisioningJob job = jobRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Provisioning job not found"));
        List<ProvisioningStepResponse> steps = stepRepository.findByJobIdOrderByIdAsc(job.getId()).stream()
                .map(step -> new ProvisioningStepResponse(step.getStepName(), step.getStatus(), step.getMessage(),
                        step.getStartedAt(), step.getFinishedAt()))
                .toList();
        return new ProvisioningStatusResponse(job.getId(), job.getStatus(), steps);
    }

    @Transactional
    public void markJobRunning(ProvisioningJob job) {
        job.setStatus("RUNNING");
        job.setUpdatedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    @Transactional
    public void markJobCompleted(ProvisioningJob job) {
        job.setStatus("COMPLETED");
        job.setUpdatedAt(LocalDateTime.now());
        jobRepository.save(job);
        Organization organization = organizationRepository.findById(job.getOrganizationId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Organization not found"));
        organization.setStatus("ACTIVE");
        organization.setUpdatedAt(LocalDateTime.now());
        organizationRepository.save(organization);
    }

    @Transactional
    public void markJobFailed(ProvisioningJob job, String message) {
        job.setStatus("FAILED");
        job.setUpdatedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    @Transactional
    public void startStep(ProvisioningStep step) {
        step.setStatus("RUNNING");
        step.setStartedAt(LocalDateTime.now());
        stepRepository.save(step);
    }

    @Transactional
    public void succeedStep(ProvisioningStep step, String message) {
        step.setStatus("SUCCEEDED");
        step.setMessage(message);
        step.setFinishedAt(LocalDateTime.now());
        stepRepository.save(step);
    }

    @Transactional
    public void failStep(ProvisioningStep step, String message) {
        step.setStatus("FAILED");
        step.setMessage(message);
        step.setFinishedAt(LocalDateTime.now());
        stepRepository.save(step);
    }
}
