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
package com.somimas.fineract.saas.identity;

import java.util.Optional;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.database.RoutingDataSourceServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExternalIdentityAppUserService {

    @Autowired(required = false)
    private RoutingDataSourceServiceFactory routingDataSourceServiceFactory;

    public Optional<Long> findAppUserId(String issuer, String subject) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(resolveTenantDataSource());
        try {
            Long appUserId = jdbcTemplate.queryForObject(
                    "SELECT appuser_id FROM m_appuser_external_identity WHERE issuer = ? AND subject = ?", Long.class, issuer, subject);
            return Optional.ofNullable(appUserId);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void linkAppUser(Long appUserId, String issuer, String subject) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(resolveTenantDataSource());
        jdbcTemplate.update("INSERT INTO m_appuser_external_identity (appuser_id, issuer, subject) VALUES (?, ?, ?)", appUserId, issuer,
                subject);
    }

    private DataSource resolveTenantDataSource() {
        if (routingDataSourceServiceFactory == null) {
            throw new IllegalStateException("RoutingDataSourceServiceFactory is unavailable; cannot resolve tenant datasource");
        }
        return routingDataSourceServiceFactory.determineDataSourceService().retrieveDataSource();
    }
}
