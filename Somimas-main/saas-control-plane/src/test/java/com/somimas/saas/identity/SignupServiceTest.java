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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.somimas.saas.audit.AuditService;
import com.somimas.saas.billing.Plan;
import com.somimas.saas.billing.PlanRepository;
import com.somimas.saas.billing.Subscription;
import com.somimas.saas.billing.SubscriptionService;
import com.somimas.saas.organization.Organization;
import com.somimas.saas.organization.OrganizationMembershipRepository;
import com.somimas.saas.organization.OrganizationRepository;
import com.somimas.saas.provisioning.ProvisioningCredential;
import com.somimas.saas.provisioning.ProvisioningCredentialRepository;
import com.somimas.saas.provisioning.ProvisioningService;
import com.somimas.saas.web.dto.request.SignupRequest;
import com.somimas.saas.web.dto.response.SignupResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    @Mock
    private IdentityRepository identityRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationMembershipRepository membershipRepository;
    @Mock
    private PlanRepository planRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private ProvisioningService provisioningService;
    @Mock
    private ProvisioningCredentialRepository provisioningCredentialRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SlugValidator slugValidator;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private SignupService signupService;

    @Test
    void signupCreatesIdentityOrgMembershipTrialCredentialAndProvisioningJob() {
        SignupRequest request = new SignupRequest("owner@example.com", "password123", "Ada", "Lovelace", "Acme MFI");
        when(slugValidator.generateUniqueSlug("Acme MFI")).thenReturn("acme-mfi");
        when(identityRepository.findByEmail("owner@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hash");

        Identity savedIdentity = new Identity();
        savedIdentity.setId(1L);
        savedIdentity.setEmail("owner@example.com");
        when(identityRepository.save(any(Identity.class))).thenReturn(savedIdentity);

        Organization savedOrg = new Organization();
        savedOrg.setId(10L);
        savedOrg.setSlug("acme-mfi");
        when(organizationRepository.save(any(Organization.class))).thenReturn(savedOrg);

        Plan starter = new Plan();
        starter.setId(100L);
        starter.setCode("starter");
        when(planRepository.findByCode("starter")).thenReturn(Optional.of(starter));

        Subscription subscription = new Subscription();
        subscription.setId(200L);
        when(subscriptionService.createTrialSubscription(10L, 100L)).thenReturn(subscription);
        when(provisioningService.createPendingJob(10L)).thenReturn(500L);
        when(provisioningCredentialRepository.save(any(ProvisioningCredential.class))).thenAnswer(inv -> inv.getArgument(0));

        SignupResponse response = signupService.signup(request);

        assertEquals(10L, response.organizationId());
        assertEquals(500L, response.jobId());
        assertEquals("acme-mfi", response.slug());
        verify(membershipRepository).save(any());
        ArgumentCaptor<ProvisioningCredential> credentialCaptor = ArgumentCaptor.forClass(ProvisioningCredential.class);
        verify(provisioningCredentialRepository).save(credentialCaptor.capture());
        ProvisioningCredential credential = credentialCaptor.getValue();
        assertEquals("owner@example.com", credential.getAdminUsername());
        assertEquals("Ada", credential.getFirstName());
        assertTrue(credential.getPasswordHash().startsWith("{bcrypt}"));
        verify(auditService).record(1L, 10L, "organization.signup", "organization", 10L, "{\"slug\":\"acme-mfi\"}");
        assertNotNull(response);
    }
}
