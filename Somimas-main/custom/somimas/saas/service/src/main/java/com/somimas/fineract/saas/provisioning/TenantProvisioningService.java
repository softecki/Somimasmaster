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

import com.somimas.fineract.saas.config.SaasBridgeProperties;
import com.somimas.fineract.saas.provisioning.dto.TenantProvisioningRequest;
import com.somimas.fineract.saas.provisioning.dto.TenantProvisioningResult;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.migration.TenantDatabaseUpgradeServiceFacade;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "somimas.saas.enabled", havingValue = "true")
public class TenantProvisioningService {

    private static final String PROVISION_LOCK_NAME = "somimas_tenant_provision";
    private static final int LOCK_TIMEOUT_SECONDS = 60;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SaasBridgeProperties saasBridgeProperties;
    private final TenantRegistryWriteService tenantRegistryWriteService;
    private final TenantDatabaseUpgradeServiceFacade tenantDatabaseUpgradeService;

    public TenantProvisioningResult provisionTenant(TenantProvisioningRequest request) {
        String identifier = request.getIdentifier();
        TenantIdentifierValidator.validate(identifier);

        if (tenantRegistryWriteService.tenantExists(identifier)) {
            upgradeTenantSchema(identifier);
            return tenantRegistryWriteService.findTenant(identifier)
                    .map(record -> TenantProvisioningResult.builder().identifier(identifier)
                            .status(TenantProvisioningResult.Status.EXISTS).databaseName(TenantIdentifierValidator.databaseName(identifier))
                            .tenantId(record.id()).message("Tenant already registered").build())
                    .orElse(TenantProvisioningResult.builder().identifier(identifier).status(TenantProvisioningResult.Status.EXISTS)
                            .databaseName(TenantIdentifierValidator.databaseName(identifier)).message("Tenant already registered").build());
        }

        String databaseName = TenantIdentifierValidator.databaseName(identifier);
        String databaseUsername = TenantIdentifierValidator.databaseUsername(identifier);
        String databasePassword = generatePassword();
        String tenantName = StringUtils.hasText(request.getName()) ? request.getName() : identifier;
        String timezoneId = StringUtils.hasText(request.getTimezoneId()) ? request.getTimezoneId() : "UTC";

        SaasBridgeProperties.Db adminDb = saasBridgeProperties.getDb();
        if (!StringUtils.hasText(adminDb.getAdminUrl()) || !StringUtils.hasText(adminDb.getAdminUsername())) {
            throw new IllegalStateException("somimas.saas.db.admin-url and somimas.saas.db.admin-username must be configured");
        }

        try (Connection adminConnection = java.sql.DriverManager.getConnection(adminDb.getAdminUrl(), adminDb.getAdminUsername(),
                adminDb.getAdminPassword())) {
            adminConnection.setAutoCommit(true);
            if (!acquireLock(adminConnection)) {
                throw new IllegalStateException("Unable to acquire MariaDB advisory lock for tenant provisioning");
            }
            try {
                if (tenantRegistryWriteService.tenantExists(identifier)) {
                    return TenantProvisioningResult.builder().identifier(identifier).status(TenantProvisioningResult.Status.EXISTS)
                            .databaseName(databaseName).message("Tenant already registered").build();
                }

                createDatabase(adminConnection, databaseName);
                createUserAndGrant(adminConnection, databaseUsername, databasePassword, databaseName);

                TenantRegistryWriteService.TenantRegistryRecord record = tenantRegistryWriteService.insertTenant(identifier, tenantName,
                        timezoneId, databaseName, databaseUsername, databasePassword, saasBridgeProperties.isEncryptPasswords());
                upgradeTenantSchema(identifier);

                log.info("Provisioned tenant {} with database {}", identifier, databaseName);

                return TenantProvisioningResult.builder().identifier(identifier).status(TenantProvisioningResult.Status.CREATED)
                        .databaseName(databaseName).tenantId(record.id()).message("Tenant provisioned successfully").build();
            } finally {
                releaseLock(adminConnection);
            }
        } catch (Exception e) {
            log.error("Failed to provision tenant {}", identifier, e);
            return TenantProvisioningResult.builder().identifier(identifier).status(TenantProvisioningResult.Status.FAILED)
                    .databaseName(databaseName).message("Provisioning failed: " + e.getMessage()).build();
        }
    }

    public TenantProvisioningResult getStatus(String identifier) {
        TenantIdentifierValidator.validate(identifier);
        return tenantRegistryWriteService.findTenant(identifier)
                .map(record -> TenantProvisioningResult.builder().identifier(identifier).status(TenantProvisioningResult.Status.EXISTS)
                        .databaseName(TenantIdentifierValidator.databaseName(identifier)).tenantId(record.id())
                        .message("accessState=" + record.accessState()).build())
                .orElse(TenantProvisioningResult.builder().identifier(identifier).status(TenantProvisioningResult.Status.FAILED)
                        .databaseName(TenantIdentifierValidator.databaseName(identifier)).message("Tenant not found").build());
    }

    private boolean acquireLock(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT GET_LOCK('" + PROVISION_LOCK_NAME + "', " + LOCK_TIMEOUT_SECONDS + ")")) {
            if (rs.next()) {
                return rs.getInt(1) == 1;
            }
            return false;
        }
    }

    private void releaseLock(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT RELEASE_LOCK('" + PROVISION_LOCK_NAME + "')");
        }
    }

    private void createDatabase(Connection connection, String databaseName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS `" + databaseName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private void createUserAndGrant(Connection connection, String username, String password, String databaseName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE USER IF NOT EXISTS '" + username + "'@'%' IDENTIFIED BY '" + escapeSqlLiteral(password) + "'");
            statement.execute("GRANT ALL PRIVILEGES ON `" + databaseName + "`.* TO '" + username + "'@'%'");
            statement.execute("FLUSH PRIVILEGES");
        }
    }

    private static String generatePassword() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(32);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static String escapeSqlLiteral(String value) {
        return value.replace("'", "''");
    }

    private void upgradeTenantSchema(String identifier) {
        try {
            tenantRegistryWriteService.evictTenantCache(identifier);
            tenantDatabaseUpgradeService.upgradeTenantByIdentifier(identifier);
        } catch (Exception e) {
            throw new IllegalStateException("Tenant database migration failed for " + identifier, e);
        }
    }
}
