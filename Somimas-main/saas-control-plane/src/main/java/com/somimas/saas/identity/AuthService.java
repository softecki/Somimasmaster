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
import com.somimas.saas.config.LocalAuthSupport;
import com.somimas.saas.config.SomimasProperties;
import com.somimas.saas.organization.OrganizationMembership;
import com.somimas.saas.organization.OrganizationMembershipRepository;
import com.somimas.saas.web.ApiException;
import com.somimas.saas.web.dto.request.LoginRequest;
import com.somimas.saas.web.dto.response.LoginResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final IdentityRepository identityRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final LocalAuthSupport localAuthSupport;
    private final AuditService auditService;
    private final SomimasProperties somimasProperties;

    public AuthService(IdentityRepository identityRepository, OrganizationMembershipRepository membershipRepository,
            PasswordEncoder passwordEncoder, LocalAuthSupport localAuthSupport, AuditService auditService,
            SomimasProperties somimasProperties) {
        this.identityRepository = identityRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.localAuthSupport = localAuthSupport;
        this.auditService = auditService;
        this.somimasProperties = somimasProperties;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Identity identity = identityRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!"ACTIVE".equals(identity.getStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Account is not active");
        }
        if (identity.getPasswordHash() == null || !passwordEncoder.matches(request.password(), identity.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        List<String> roles = new ArrayList<>();
        roles.add("USER");
        for (OrganizationMembership membership : membershipRepository.findByIdentityId(identity.getId())) {
            if ("OWNER".equals(membership.getRole())) {
                roles.add("ORG_OWNER");
            }
        }
        if (isPlatformAdmin(identity.getEmail())) {
            roles.add("PLATFORM_ADMIN");
        }
        String token = localAuthSupport.encodeToken(identity.getId(), identity.getEmail(), roles);
        auditService.record(identity.getId(), null, "identity.login", "identity", identity.getId(), null);
        return new LoginResponse(token, identity.getId(), identity.getEmail(),
                roles.stream().map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role).toList());
    }

    private boolean isPlatformAdmin(String email) {
        return java.util.Arrays.stream(somimasProperties.getAuth().getPlatformAdminEmails().split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .anyMatch(value -> value.equalsIgnoreCase(email));
    }
}
