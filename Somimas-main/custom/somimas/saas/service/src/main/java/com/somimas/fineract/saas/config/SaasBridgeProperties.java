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
package com.somimas.fineract.saas.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "somimas.saas")
public class SaasBridgeProperties {

    private boolean enabled;

    private Bridge bridge = new Bridge();

    private Db db = new Db();

    /**
     * When true (default), tenant DB passwords are encrypted via {@code DatabasePasswordEncryptor}.
     * When false, passwords are stored in plaintext in tenant_server_connections (development only).
     */
    private boolean encryptPasswords = true;

    private Entitlement entitlement = new Entitlement();

    @Getter
    @Setter
    public static class Bridge {

        private String token;
    }

    @Getter
    @Setter
    public static class Db {

        private String adminUrl;

        private String adminUsername;

        private String adminPassword;
    }

    @Getter
    @Setter
    public static class Entitlement {

        /**
         * When true, entitlement violations are logged but requests are not blocked.
         */
        private boolean reportOnly = true;
    }
}
