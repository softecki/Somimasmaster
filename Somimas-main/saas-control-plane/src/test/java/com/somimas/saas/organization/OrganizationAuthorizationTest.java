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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.somimas.saas.web.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationAuthorizationTest {

    @Mock
    private OrganizationMembershipRepository membershipRepository;

    @InjectMocks
    private OrganizationAuthorizationService organizationAuthorizationService;

    @Test
    void requireMembershipAllowsMember() {
        when(membershipRepository.existsByOrganizationIdAndIdentityId(10L, 1L)).thenReturn(true);
        assertDoesNotThrow(() -> organizationAuthorizationService.requireMembership(1L, 10L));
    }

    @Test
    void requireMembershipRejectsNonMember() {
        when(membershipRepository.existsByOrganizationIdAndIdentityId(10L, 1L)).thenReturn(false);
        assertThrows(ApiException.class, () -> organizationAuthorizationService.requireMembership(1L, 10L));
    }

    @Test
    void requireRoleChecksRole() {
        OrganizationMembership membership = new OrganizationMembership();
        membership.setRole("OWNER");
        when(membershipRepository.findByOrganizationIdAndIdentityId(10L, 1L)).thenReturn(Optional.of(membership));
        assertDoesNotThrow(() -> organizationAuthorizationService.requireRole(1L, 10L, "OWNER"));
    }

    @Test
    void requireRoleRejectsWrongRole() {
        OrganizationMembership membership = new OrganizationMembership();
        membership.setRole("MEMBER");
        when(membershipRepository.findByOrganizationIdAndIdentityId(10L, 1L)).thenReturn(Optional.of(membership));
        assertThrows(ApiException.class, () -> organizationAuthorizationService.requireRole(1L, 10L, "OWNER"));
    }
}
