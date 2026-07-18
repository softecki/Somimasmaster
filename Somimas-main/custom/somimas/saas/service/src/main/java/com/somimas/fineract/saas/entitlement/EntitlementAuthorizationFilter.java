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
package com.somimas.fineract.saas.entitlement;

import com.somimas.fineract.saas.config.SaasBridgeProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public class EntitlementAuthorizationFilter extends OncePerRequestFilter {

    private final EntitlementRoutePolicy entitlementRoutePolicy;
    private final boolean reportOnly;

    public EntitlementAuthorizationFilter(SaasBridgeProperties saasBridgeProperties) {
        this.reportOnly = saasBridgeProperties.getEntitlement().isReportOnly();
        this.entitlementRoutePolicy = new EntitlementRoutePolicy(Set.of("/api/**/clients/**", "/api/**/loans/**", "/api/**/savingsaccounts/**"));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!entitlementRoutePolicy.isAllowed(path, request.getMethod())) {
            String message = "Route not entitled: " + request.getMethod() + " " + path;
            if (reportOnly) {
                log.warn("[report-only] {}", message);
            } else {
                response.sendError(HttpStatus.FORBIDDEN.value(), message);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
