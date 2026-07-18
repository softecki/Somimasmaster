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

import java.util.Set;

public final class TenantAccessPolicy {

    public static final String ACTIVE = "ACTIVE";
    public static final String SUSPENDED = "SUSPENDED";

    private static final Set<String> ALLOWED_STATES = Set.of(ACTIVE, SUSPENDED);

    private TenantAccessPolicy() {}

    public static void validateAccessState(String accessState) {
        if (accessState == null || !ALLOWED_STATES.contains(accessState)) {
            throw new IllegalArgumentException("accessState must be one of " + ALLOWED_STATES);
        }
    }

    public static boolean isAccessAllowed(String accessState) {
        return ACTIVE.equals(accessState);
    }
}
