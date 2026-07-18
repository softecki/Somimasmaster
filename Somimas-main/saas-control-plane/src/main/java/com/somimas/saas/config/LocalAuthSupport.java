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
package com.somimas.saas.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class LocalAuthSupport {

    private final ObjectMapper objectMapper;
    private final SomimasProperties somimasProperties;

    public LocalAuthSupport(ObjectMapper objectMapper, SomimasProperties somimasProperties) {
        this.objectMapper = objectMapper;
        this.somimasProperties = somimasProperties;
    }

    public String encodeToken(Long sub, String email, List<String> roles) {
        try {
            long expiresAt = Instant.now().plusSeconds(12 * 60 * 60).getEpochSecond();
            Map<String, Object> payload = Map.of("sub", String.valueOf(sub), "email", email, "roles", roles, "exp", expiresAt);
            String json = objectMapper.writeValueAsString(payload);
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            return encodedPayload + "." + sign(encodedPayload);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encode local token", ex);
        }
    }

    public LocalTokenClaims decodeToken(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]).getBytes(StandardCharsets.US_ASCII),
                    parts[1].getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("Invalid token signature");
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[0]);
            Map<String, Object> payload = objectMapper.readValue(decoded, new TypeReference<>() {});
            long expiresAt = Long.parseLong(String.valueOf(payload.get("exp")));
            if (Instant.now().getEpochSecond() >= expiresAt) {
                throw new IllegalArgumentException("Token has expired");
            }
            String sub = String.valueOf(payload.get("sub"));
            String email = String.valueOf(payload.get("email"));
            Object rolesObj = payload.get("roles");
            List<String> roles;
            if (rolesObj instanceof List<?> list) {
                roles = list.stream().map(String::valueOf).toList();
            } else {
                roles = Collections.emptyList();
            }
            return new LocalTokenClaims(Long.parseLong(sub), email, roles);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid local bearer token", ex);
        }
    }

    private String sign(String payload) {
        String secret = somimasProperties.getAuth().getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("SOMIMAS_AUTH_JWT_SECRET must contain at least 32 bytes");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign local token", ex);
        }
    }

    public record LocalTokenClaims(Long identityId, String email, List<String> roles) {}
}
