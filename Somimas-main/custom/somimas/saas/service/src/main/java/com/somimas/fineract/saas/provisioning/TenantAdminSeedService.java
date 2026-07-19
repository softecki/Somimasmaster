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

import com.somimas.fineract.saas.provisioning.dto.AdminSeedRequest;
import com.somimas.fineract.saas.provisioning.dto.AdminSeedResult;
import java.sql.Date;
import java.time.LocalDate;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.database.RoutingDataSourceServiceFactory;
import org.apache.fineract.infrastructure.core.service.tenant.JdbcTenantDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Idempotently seeds the organization owner as a Fineract {@code m_appuser} with the Super user role.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "somimas.saas.enabled", havingValue = "true")
public class TenantAdminSeedService {

    private static final long SUPER_USER_ROLE_ID = 1L;

    @Autowired(required = false)
    private JdbcTenantDetailsService jdbcTenantDetailsService;

    @Autowired(required = false)
    private RoutingDataSourceServiceFactory routingDataSourceServiceFactory;

    public AdminSeedResult seedAdmin(String identifier, AdminSeedRequest request) {
        TenantIdentifierValidator.validate(identifier);
        if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPasswordHash())
                || !StringUtils.hasText(request.getEmail()) || !StringUtils.hasText(request.getFirstName())
                || !StringUtils.hasText(request.getLastName())) {
            return AdminSeedResult.builder().identifier(identifier).status(AdminSeedResult.Status.FAILED)
                    .message("username, email, firstName, lastName and passwordHash are required").build();
        }
        if (!request.getPasswordHash().startsWith("{")) {
            return AdminSeedResult.builder().identifier(identifier).status(AdminSeedResult.Status.FAILED)
                    .message("passwordHash must be a DelegatingPasswordEncoder hash (e.g. {bcrypt}...)").build();
        }

        if (jdbcTenantDetailsService == null || routingDataSourceServiceFactory == null) {
            return AdminSeedResult.builder().identifier(identifier).status(AdminSeedResult.Status.FAILED)
                    .message("Tenant datasource services unavailable").build();
        }

        FineractPlatformTenant tenant;
        try {
            tenant = jdbcTenantDetailsService.loadTenantById(identifier);
        } catch (Exception ex) {
            return AdminSeedResult.builder().identifier(identifier).status(AdminSeedResult.Status.FAILED)
                    .message("Tenant not found: " + identifier).build();
        }

        try {
            ThreadLocalContextUtil.setTenant(tenant);
            JdbcTemplate jdbc = new JdbcTemplate(resolveTenantDataSource());

            Long existingId = findUserId(jdbc, request.getUsername());
            if (existingId != null) {
                ensureSuperUserRole(jdbc, existingId);
                log.info("Admin user already exists for tenant {} (appUserId={})", identifier, existingId);
                return AdminSeedResult.builder().identifier(identifier).status(AdminSeedResult.Status.EXISTS).appUserId(existingId)
                        .message("Admin user already exists").build();
            }

            Long officeId = resolveHeadOfficeId(jdbc);
            Long appUserId = insertAppUser(jdbc, officeId, request);
            ensureSuperUserRole(jdbc, appUserId);
            log.info("Seeded admin user for tenant {} (appUserId={})", identifier, appUserId);
            return AdminSeedResult.builder().identifier(identifier).status(AdminSeedResult.Status.CREATED).appUserId(appUserId)
                    .message("Admin user created").build();
        } catch (Exception ex) {
            log.error("Failed to seed admin for tenant {}", identifier, ex);
            return AdminSeedResult.builder().identifier(identifier).status(AdminSeedResult.Status.FAILED)
                    .message("Admin seeding failed: " + ex.getMessage()).build();
        } finally {
            ThreadLocalContextUtil.reset();
        }
    }

    private DataSource resolveTenantDataSource() {
        return routingDataSourceServiceFactory.determineDataSourceService().retrieveDataSource();
    }

    private Long findUserId(JdbcTemplate jdbc, String username) {
        try {
            return jdbc.queryForObject("SELECT id FROM m_appuser WHERE username = ? AND is_deleted = false", Long.class, username);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private Long resolveHeadOfficeId(JdbcTemplate jdbc) {
        try {
            return jdbc.queryForObject("SELECT id FROM m_office WHERE parent_id IS NULL ORDER BY id ASC LIMIT 1", Long.class);
        } catch (EmptyResultDataAccessException e) {
            return 1L;
        }
    }

    private Long insertAppUser(JdbcTemplate jdbc, Long officeId, AdminSeedRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        Date today = Date.valueOf(LocalDate.now());
        jdbc.update(connection -> {
            var ps = connection.prepareStatement(
                    """
                            INSERT INTO m_appuser
                            (is_deleted, office_id, staff_id, username, firstname, lastname, password, email,
                             firsttime_login_remaining, nonexpired, nonlocked, nonexpired_credentials, enabled,
                             last_time_password_updated, password_never_expires, is_self_service_user)
                            VALUES (false, ?, NULL, ?, ?, ?, ?, ?, false, true, true, true, true, ?, false, false)
                            """,
                    new String[] { "id" });
            ps.setLong(1, officeId);
            ps.setString(2, request.getUsername());
            ps.setString(3, request.getFirstName());
            ps.setString(4, request.getLastName());
            ps.setString(5, request.getPasswordHash());
            ps.setString(6, request.getEmail());
            ps.setDate(7, today);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain generated app user id");
        }
        return key.longValue();
    }

    private void ensureSuperUserRole(JdbcTemplate jdbc, Long appUserId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM m_appuser_role WHERE appuser_id = ? AND role_id = ?", Integer.class,
                appUserId, SUPER_USER_ROLE_ID);
        if (count == null || count == 0) {
            jdbc.update("INSERT INTO m_appuser_role (appuser_id, role_id) VALUES (?, ?)", appUserId, SUPER_USER_ROLE_ID);
        }
    }
}
