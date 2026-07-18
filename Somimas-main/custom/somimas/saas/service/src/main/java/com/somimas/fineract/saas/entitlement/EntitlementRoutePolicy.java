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

import java.util.Set;
import org.springframework.util.AntPathMatcher;

public class EntitlementRoutePolicy {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final Set<String> allowedPatterns;

    public EntitlementRoutePolicy(Set<String> allowedPatterns) {
        this.allowedPatterns = allowedPatterns;
    }

    public boolean isAllowed(String requestPath, String httpMethod) {
        if (requestPath == null) {
            return false;
        }
        for (String pattern : allowedPatterns) {
            if (PATH_MATCHER.match(pattern, requestPath)) {
                return true;
            }
        }
        return false;
    }
}
