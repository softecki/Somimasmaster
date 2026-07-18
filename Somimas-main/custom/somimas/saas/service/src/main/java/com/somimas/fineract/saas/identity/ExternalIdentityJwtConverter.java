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

import org.springframework.stereotype.Component;

/**
 * Stub documenting how external JWT identities map to Fineract {@code m_appuser} rows.
 *
 * <p>
 * Expected JWT claims:
 * </p>
 * <ul>
 * <li>{@code iss} → {@code m_appuser_external_identity.issuer}</li>
 * <li>{@code sub} → {@code m_appuser_external_identity.subject}</li>
 * </ul>
 *
 * <p>
 * At authentication time, resolve {@code appuser_id} via {@link ExternalIdentityAppUserService}
 * and load the corresponding {@code m_appuser} record for Spring Security.
 * </p>
 */
@Component
public class ExternalIdentityJwtConverter {

    public ExternalIdentityClaims convert(String issuer, String subject) {
        return new ExternalIdentityClaims(issuer, subject);
    }

    public record ExternalIdentityClaims(String issuer, String subject) {}
}
