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
package com.somimas.saas.provisioning;

import com.somimas.saas.config.SomimasProperties;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FineractProvisioningClient {

    private final RestClient restClient;
    private final SomimasProperties somimasProperties;

    public FineractProvisioningClient(RestClient restClient, SomimasProperties somimasProperties) {
        this.restClient = restClient;
        this.somimasProperties = somimasProperties;
    }

    public BridgeProvisioningResult createTenant(String identifier, String name) {
        String url = somimasProperties.getFineract().getBridgeUrl() + "/internal/saas/tenants";
        Map<String, String> body = Map.of("identifier", identifier, "name", name, "timezoneId", "UTC");
        return restClient.post()
                .uri(url)
                .header("X-Somimas-Bridge-Token", somimasProperties.getFineract().getBridgeToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(BridgeProvisioningResult.class);
    }

    public BridgeProvisioningResult getTenantStatus(String identifier) {
        String url = somimasProperties.getFineract().getBridgeUrl() + "/internal/saas/tenants/status/" + identifier;
        return restClient.get()
                .uri(url)
                .header("X-Somimas-Bridge-Token", somimasProperties.getFineract().getBridgeToken())
                .retrieve()
                .body(BridgeProvisioningResult.class);
    }

    public BridgeAdminSeedResult seedAdmin(String identifier, String username, String email, String firstName, String lastName,
            String passwordHash) {
        String url = somimasProperties.getFineract().getBridgeUrl() + "/internal/saas/tenants/" + identifier + "/admin";
        Map<String, String> body = Map.of(
                "username", username,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "passwordHash", passwordHash);
        return restClient.post()
                .uri(url)
                .header("X-Somimas-Bridge-Token", somimasProperties.getFineract().getBridgeToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(BridgeAdminSeedResult.class);
    }

    public void updateAccessState(String identifier, String accessState) {
        String url = somimasProperties.getFineract().getBridgeUrl() + "/internal/saas/tenants/" + identifier + "/access-state";
        restClient.post()
                .uri(url)
                .header("X-Somimas-Bridge-Token", somimasProperties.getFineract().getBridgeToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("accessState", accessState))
                .retrieve()
                .toBodilessEntity();
    }

    public record BridgeProvisioningResult(String identifier, String status, String databaseName, Long tenantId,
            String message) {}

    public record BridgeAdminSeedResult(String identifier, String status, Long appUserId, String message) {}
}
