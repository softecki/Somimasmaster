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
package com.somimas.fineract.saas.access;

import com.somimas.fineract.saas.provisioning.TenantAccessStateService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Blocks API traffic for tenants whose {@code tenants.access_state} is not {@code ACTIVE}.
 *
 * <p>
 * Integration with {@code TenantAwareBasicAuthenticationFilter}: register this filter immediately
 * after {@code TenantAwareBasicAuthenticationFilter} in {@code SecurityConfig} so the tenant is
 * already present in {@link ThreadLocalContextUtil} when access state is evaluated.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class TenantAccessFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "Fineract-Platform-TenantId";

    private final TenantAccessStateService tenantAccessStateService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        FineractPlatformTenant tenant = ThreadLocalContextUtil.getTenant();
        String tenantIdentifier = tenant != null ? tenant.getTenantIdentifier() : request.getHeader(TENANT_HEADER);
        if (tenantIdentifier == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String accessState = tenantAccessStateService.getAccessState(tenantIdentifier);
            if (!TenantAccessPolicy.isAccessAllowed(accessState)) {
                throw new TenantSuspendedException(tenantIdentifier);
            }
        } catch (TenantSuspendedException e) {
            response.sendError(HttpStatus.FORBIDDEN.value(), e.getMessage());
            return;
        } catch (RuntimeException e) {
            log.debug("Skipping tenant access check for {}: {}", tenantIdentifier, e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
