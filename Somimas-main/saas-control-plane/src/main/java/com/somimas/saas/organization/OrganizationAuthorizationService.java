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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationAuthorizationService {

    private final OrganizationMembershipRepository membershipRepository;

    public OrganizationAuthorizationService(OrganizationMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public void requireMembership(Long identityId, Long organizationId) {
        if (!membershipRepository.existsByOrganizationIdAndIdentityId(organizationId, identityId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Not a member of this organization");
        }
    }

    @Transactional(readOnly = true)
    public void requireRole(Long identityId, Long organizationId, String role) {
        OrganizationMembership membership = membershipRepository
                .findByOrganizationIdAndIdentityId(organizationId, identityId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Not a member of this organization"));
        if (!role.equals(membership.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Insufficient organization role");
        }
    }

    @Transactional(readOnly = true)
    public boolean isMember(Long identityId, Long organizationId) {
        return membershipRepository.existsByOrganizationIdAndIdentityId(organizationId, identityId);
    }
}
