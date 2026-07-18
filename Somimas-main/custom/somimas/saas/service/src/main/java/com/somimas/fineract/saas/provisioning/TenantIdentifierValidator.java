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
package com.somimas.fineract.saas.provisioning;

import java.util.regex.Pattern;

public final class TenantIdentifierValidator {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$");

    private TenantIdentifierValidator() {}

    public static void validate(String identifier) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                    "Invalid tenant identifier '" + identifier + "'. Must match ^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$");
        }
    }

    public static String databaseName(String identifier) {
        validate(identifier);
        return "somimas_" + identifier.replace('-', '_');
    }

    public static String databaseUsername(String identifier) {
        validate(identifier);
        // MariaDB user names should avoid hyphens.
        return "somimas_" + identifier.replace('-', '_');
    }
}
