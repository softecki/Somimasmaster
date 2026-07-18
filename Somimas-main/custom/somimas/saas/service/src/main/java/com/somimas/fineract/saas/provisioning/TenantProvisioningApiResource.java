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

import com.somimas.fineract.saas.provisioning.dto.AccessStateRequest;
import com.somimas.fineract.saas.provisioning.dto.TenantProvisioningRequest;
import com.somimas.fineract.saas.provisioning.dto.TenantProvisioningResult;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/saas/tenants")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "somimas.saas.enabled", havingValue = "true")
public class TenantProvisioningApiResource {

    private final TenantProvisioningService tenantProvisioningService;
    private final TenantAccessStateService tenantAccessStateService;

    @PostMapping
    public ResponseEntity<TenantProvisioningResult> createTenant(@RequestBody TenantProvisioningRequest request) {
        TenantProvisioningResult result = tenantProvisioningService.provisionTenant(request);
        HttpStatus status = result.getStatus() == TenantProvisioningResult.Status.FAILED ? HttpStatus.BAD_REQUEST : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping("/status/{identifier}")
    public ResponseEntity<TenantProvisioningResult> getStatus(@PathVariable("identifier") String identifier) {
        TenantProvisioningResult result = tenantProvisioningService.getStatus(identifier);
        if (result.getStatus() == TenantProvisioningResult.Status.FAILED && "Tenant not found".equals(result.getMessage())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{identifier}/access-state")
    public ResponseEntity<Map<String, String>> updateAccessState(@PathVariable("identifier") String identifier,
            @RequestBody AccessStateRequest request) {
        tenantAccessStateService.updateAccessState(identifier, request.getAccessState());
        return ResponseEntity.ok(Map.of("identifier", identifier, "accessState", request.getAccessState()));
    }
}
