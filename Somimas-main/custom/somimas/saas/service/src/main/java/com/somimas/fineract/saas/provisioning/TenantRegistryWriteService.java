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

import java.util.Optional;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.service.database.DatabasePasswordEncryptor;
import org.apache.fineract.infrastructure.core.service.tenant.JdbcTenantDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "somimas.saas.enabled", havingValue = "true")
public class TenantRegistryWriteService {

    private static final String DEFAULT_ACCESS_STATE = "ACTIVE";

    private final DataSource tenantStoreDataSource;
    private final FineractProperties fineractProperties;

    @Autowired(required = false)
    private DatabasePasswordEncryptor databasePasswordEncryptor;

    @Autowired(required = false)
    private JdbcTenantDetailsService jdbcTenantDetailsService;

    @Autowired
    public TenantRegistryWriteService(@Qualifier("hikariTenantDataSource") final DataSource tenantStoreDataSource,
            final FineractProperties fineractProperties) {
        this.tenantStoreDataSource = tenantStoreDataSource;
        this.fineractProperties = fineractProperties;
    }

    public boolean tenantExists(String identifier) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(tenantStoreDataSource);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM tenants WHERE identifier = ?", Integer.class, identifier);
        return count != null && count > 0;
    }

    public Optional<TenantRegistryRecord> findTenant(String identifier) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(tenantStoreDataSource);
        try {
            return Optional.of(jdbcTemplate.queryForObject(
                    "SELECT id, identifier, name, timezone_id, access_state FROM tenants WHERE identifier = ?",
                    (rs, rowNum) -> new TenantRegistryRecord(rs.getLong("id"), rs.getString("identifier"), rs.getString("name"),
                            rs.getString("timezone_id"), rs.getString("access_state")),
                    identifier));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public TenantRegistryRecord insertTenant(String identifier, String name, String timezoneId, String schemaName, String schemaUsername,
            String schemaPassword, boolean encryptPasswords) {
        FineractProperties.FineractTenantProperties tenantDefaults = fineractProperties.getTenant();
        String storedPassword = resolveStoredPassword(schemaPassword, encryptPasswords);
        String masterPasswordHash = resolveMasterPasswordHash(encryptPasswords);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(tenantStoreDataSource);

        KeyHolder connectionKeyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    """
                            INSERT INTO tenant_server_connections
                            (schema_server, schema_name, schema_server_port, schema_username, schema_password,
                             auto_update, pool_initial_size, pool_validation_interval, pool_remove_abandoned,
                             pool_remove_abandoned_timeout, pool_log_abandoned, pool_abandon_when_percentage_full,
                             pool_test_on_borrow, pool_max_active, pool_min_idle, pool_max_idle,
                             pool_suspect_timeout, pool_time_between_eviction_runs_millis,
                             pool_min_evictable_idle_time_millis, schema_connection_parameters, master_password_hash)
                            VALUES (?, ?, ?, ?, ?, 1, 5, 30000, 1, 60, 1, 50, 1, 40, 20, 10, 60, 34000, 60000, ?, ?)
                            """,
                    new String[] { "id" });
            ps.setString(1, tenantDefaults.getHost());
            ps.setString(2, schemaName);
            ps.setString(3, String.valueOf(tenantDefaults.getPort()));
            ps.setString(4, schemaUsername);
            ps.setString(5, storedPassword);
            ps.setString(6, tenantDefaults.getParameters());
            ps.setString(7, masterPasswordHash);
            return ps;
        }, connectionKeyHolder);

        Number connectionIdNumber = connectionKeyHolder.getKey();
        if (connectionIdNumber == null) {
            throw new IllegalStateException("Failed to obtain tenant_server_connections id for tenant " + identifier);
        }
        long connectionId = connectionIdNumber.longValue();

        KeyHolder tenantKeyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    """
                            INSERT INTO tenants
                            (identifier, name, timezone_id, oltp_id, report_id, access_state, access_state_changed_at)
                            VALUES (?, ?, ?, ?, ?, ?, NOW(6))
                            """,
                    new String[] { "id" });
            ps.setString(1, identifier);
            ps.setString(2, name);
            ps.setString(3, timezoneId);
            ps.setLong(4, connectionId);
            ps.setLong(5, connectionId);
            ps.setString(6, DEFAULT_ACCESS_STATE);
            return ps;
        }, tenantKeyHolder);

        Number tenantIdNumber = tenantKeyHolder.getKey();
        if (tenantIdNumber == null) {
            throw new IllegalStateException("Failed to obtain tenants id for tenant " + identifier);
        }

        evictTenantCache(identifier);

        return new TenantRegistryRecord(tenantIdNumber.longValue(), identifier, name, timezoneId, DEFAULT_ACCESS_STATE);
    }

    private String resolveStoredPassword(String plainPassword, boolean encryptPasswords) {
        if (encryptPasswords) {
            if (databasePasswordEncryptor == null) {
                throw new IllegalStateException(
                        "somimas.saas.encrypt-passwords=true but DatabasePasswordEncryptor bean is unavailable");
            }
            return databasePasswordEncryptor.encrypt(plainPassword);
        }
        // TODO: remove plaintext storage path once encryptor is always available in SaaS deployments
        log.warn("Storing tenant DB password in plaintext because somimas.saas.encrypt-passwords=false");
        return plainPassword;
    }

    private String resolveMasterPasswordHash(boolean encryptPasswords) {
        if (!encryptPasswords || databasePasswordEncryptor == null) {
            return null;
        }
        return databasePasswordEncryptor.getMasterPasswordHash();
    }

    public void evictTenantCache(String identifier) {
        if (jdbcTenantDetailsService != null) {
            jdbcTenantDetailsService.evictTenantFromCache(identifier);
        }
    }

    public record TenantRegistryRecord(Long id, String identifier, String name, String timezoneId, String accessState) {}
}
