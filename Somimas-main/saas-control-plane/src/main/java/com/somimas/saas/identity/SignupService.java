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
package com.somimas.saas.identity;

import com.somimas.saas.audit.AuditService;
import com.somimas.saas.billing.Plan;
import com.somimas.saas.billing.PlanRepository;
import com.somimas.saas.billing.SubscriptionService;
import com.somimas.saas.organization.Organization;
import com.somimas.saas.organization.OrganizationMembership;
import com.somimas.saas.organization.OrganizationMembershipRepository;
import com.somimas.saas.organization.OrganizationRepository;
import com.somimas.saas.provisioning.ProvisioningCredential;
import com.somimas.saas.provisioning.ProvisioningCredentialRepository;
import com.somimas.saas.provisioning.ProvisioningService;
import com.somimas.saas.web.ApiException;
import com.somimas.saas.web.dto.request.SignupRequest;
import com.somimas.saas.web.dto.response.SignupResponse;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupService {

    private final IdentityRepository identityRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final PlanRepository planRepository;
    private final SubscriptionService subscriptionService;
    private final ProvisioningService provisioningService;
    private final ProvisioningCredentialRepository provisioningCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final SlugValidator slugValidator;
    private final AuditService auditService;
    private final PasswordEncoder fineractPasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    public SignupService(IdentityRepository identityRepository, OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository, PlanRepository planRepository,
            SubscriptionService subscriptionService, ProvisioningService provisioningService,
            ProvisioningCredentialRepository provisioningCredentialRepository, PasswordEncoder passwordEncoder,
            SlugValidator slugValidator, AuditService auditService) {
        this.identityRepository = identityRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.planRepository = planRepository;
        this.subscriptionService = subscriptionService;
        this.provisioningService = provisioningService;
        this.provisioningCredentialRepository = provisioningCredentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.slugValidator = slugValidator;
        this.auditService = auditService;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = request.email().trim().toLowerCase();
        String firstName = request.firstName().trim();
        String lastName = request.lastName().trim();
        String organizationName = request.organizationName().trim();
        String slug = slugValidator.generateUniqueSlug(organizationName);

        if (identityRepository.findByEmail(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }

        LocalDateTime now = LocalDateTime.now();
        Identity identity = new Identity();
        identity.setEmail(email);
        identity.setPasswordHash(passwordEncoder.encode(request.password()));
        identity.setStatus("ACTIVE");
        identity.setCreatedAt(now);
        identity.setUpdatedAt(now);
        identity = identityRepository.save(identity);

        Organization organization = new Organization();
        organization.setSlug(slug);
        organization.setName(organizationName);
        organization.setStatus("PENDING");
        organization.setCreatedAt(now);
        organization.setUpdatedAt(now);
        organization = organizationRepository.save(organization);

        OrganizationMembership membership = new OrganizationMembership();
        membership.setOrganizationId(organization.getId());
        membership.setIdentityId(identity.getId());
        membership.setRole("OWNER");
        membership.setCreatedAt(now);
        membershipRepository.save(membership);

        ProvisioningCredential credential = new ProvisioningCredential();
        credential.setOrganizationId(organization.getId());
        credential.setAdminUsername(email);
        credential.setAdminEmail(email);
        credential.setFirstName(firstName);
        credential.setLastName(lastName);
        // Fineract DelegatingPasswordEncoder-compatible hash; never logged or returned.
        credential.setPasswordHash(fineractPasswordEncoder.encode(request.password()));
        credential.setCreatedAt(now);
        provisioningCredentialRepository.save(credential);

        Plan starterPlan = planRepository.findByCode("starter")
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Starter plan not configured"));
        subscriptionService.createTrialSubscription(organization.getId(), starterPlan.getId());

        Long jobId = provisioningService.createPendingJob(organization.getId());
        auditService.record(identity.getId(), organization.getId(), "organization.signup", "organization",
                organization.getId(), "{\"slug\":\"" + slug + "\"}");
        return new SignupResponse(organization.getId(), jobId, slug);
    }
}
