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

import com.somimas.fineract.saas.access.TenantAccessPolicy;
import javax.sql.DataSource;
import org.apache.fineract.infrastructure.core.service.tenant.JdbcTenantDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "somimas.saas.enabled", havingValue = "true")
public class TenantAccessStateService {

    private final DataSource tenantStoreDataSource;

    @Autowired(required = false)
    private JdbcTenantDetailsService jdbcTenantDetailsService;

    @Autowired
    public TenantAccessStateService(@Qualifier("hikariTenantDataSource") final DataSource tenantStoreDataSource) {
        this.tenantStoreDataSource = tenantStoreDataSource;
    }

    public void updateAccessState(String identifier, String accessState) {
        TenantAccessPolicy.validateAccessState(accessState);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(tenantStoreDataSource);
        int updated = jdbcTemplate.update(
                "UPDATE tenants SET access_state = ?, access_state_changed_at = NOW(6) WHERE identifier = ?", accessState, identifier);
        if (updated == 0) {
            throw new IllegalArgumentException("Tenant not found: " + identifier);
        }
        evictTenantCache(identifier);
    }

    public String getAccessState(String identifier) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(tenantStoreDataSource);
        return jdbcTemplate.queryForObject("SELECT access_state FROM tenants WHERE identifier = ?", String.class, identifier);
    }

    private void evictTenantCache(String identifier) {
        if (jdbcTenantDetailsService != null) {
            jdbcTenantDetailsService.evictTenantFromCache(identifier);
        }
    }
}
