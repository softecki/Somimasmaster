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
package com.somimas.fineract.saas.security;

import com.somimas.fineract.saas.config.SaasBridgeProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protects {@code /internal/saas/**} endpoints using the {@code X-Somimas-Bridge-Token} header.
 */
@RequiredArgsConstructor
public class SaasBridgeTokenFilter extends OncePerRequestFilter {

    public static final String BRIDGE_TOKEN_HEADER = "X-Somimas-Bridge-Token";

    private final SaasBridgeProperties saasBridgeProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (!StringUtils.hasText(path)) {
            path = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (StringUtils.hasText(contextPath) && path != null && path.startsWith(contextPath)) {
                path = path.substring(contextPath.length());
            }
        }
        return path == null || !path.startsWith("/internal/saas/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String configuredToken = saasBridgeProperties.getBridge().getToken();
        if (!StringUtils.hasText(configuredToken)) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "Somimas SaaS bridge token is not configured");
            return;
        }

        String providedToken = request.getHeader(BRIDGE_TOKEN_HEADER);
        if (!constantTimeEquals(configuredToken, providedToken)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid or missing X-Somimas-Bridge-Token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
