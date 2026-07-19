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
package com.somimas.saas.organization;

import com.somimas.saas.web.ApiException;
import com.somimas.saas.web.dto.response.OrganizationResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;

    public OrganizationService(OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> listForIdentity(Long identityId) {
        return membershipRepository.findByIdentityId(identityId).stream()
                .map(m -> organizationRepository.findById(m.getOrganizationId())
                        .map(this::toResponse)
                        .orElse(null))
                .filter(org -> org != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getById(Long organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Organization not found"));
        return toResponse(organization);
    }

    @Transactional(readOnly = true)
    public Organization getEntity(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Organization not found"));
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> listAll() {
        return organizationRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public OrganizationResponse updateStatus(Long organizationId, String status) {
        Organization organization = getEntity(organizationId);
        organization.setStatus(status);
        organization.setUpdatedAt(java.time.LocalDateTime.now());
        return toResponse(organizationRepository.save(organization));
    }

    private OrganizationResponse toResponse(Organization organization) {
        return new OrganizationResponse(organization.getId(), organization.getSlug(), organization.getName(),
                organization.getStatus(), organization.getSlug(), organization.getCreatedAt());
    }
}
