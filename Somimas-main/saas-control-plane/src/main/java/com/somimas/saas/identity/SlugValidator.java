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
package com.somimas.saas.identity;

import com.somimas.saas.organization.OrganizationRepository;
import com.somimas.saas.web.ApiException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Generates and validates organization / Fineract tenant identifiers.
 * Pattern matches the Fineract bridge {@code TenantIdentifierValidator}.
 */
@Component
public class SlugValidator {

    /** Bridge-compatible: starts and ends with alphanumeric, 3–40 chars total. */
    public static final String SLUG_PATTERN = "^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$";

    private static final Pattern PATTERN = Pattern.compile(SLUG_PATTERN);

    private static final Set<String> RESERVED = Set.of("default", "admin", "api", "www", "saas", "platform",
            "fineract", "mifos", "somimas", "root", "system", "internal", "public", "login", "signup");

    private final OrganizationRepository organizationRepository;

    public SlugValidator(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public void validate(String slug) {
        if (slug == null || !PATTERN.matcher(slug).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Invalid organization slug. Use 3–40 lowercase letters, digits, and hyphens; must start and end with a letter or digit.");
        }
        if (RESERVED.contains(slug)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Organization slug '" + slug + "' is reserved");
        }
    }

    public String generateUniqueSlug(String organizationName) {
        String base = normalize(organizationName);
        validate(base);
        String candidate = base;
        int suffix = 2;
        while (organizationRepository.findBySlug(candidate).isPresent() || RESERVED.contains(candidate)) {
            String suffixText = "-" + suffix;
            int maxBase = 40 - suffixText.length();
            String truncated = base.length() > maxBase ? base.substring(0, maxBase) : base;
            truncated = trimTrailingHyphen(truncated);
            if (truncated.length() < 2) {
                truncated = "org";
            }
            candidate = truncated + suffixText;
            // Ensure ends with digit (alphanumeric) — suffix already does
            if (!PATTERN.matcher(candidate).matches()) {
                candidate = "org" + suffixText;
            }
            suffix++;
            if (suffix > 9999) {
                throw new ApiException(HttpStatus.CONFLICT, "Unable to allocate a unique organization slug");
            }
        }
        validate(candidate);
        return candidate;
    }

    public String normalize(String organizationName) {
        if (!StringUtils.hasText(organizationName)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Organization name is required");
        }
        String slug = organizationName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (slug.length() < 3) {
            slug = (slug + "org").substring(0, Math.min(3, (slug + "org").length()));
            if (slug.length() < 3) {
                slug = "org";
            }
        }
        if (slug.length() > 40) {
            slug = trimTrailingHyphen(slug.substring(0, 40));
        }
        // Ensure starts/ends with alphanumeric
        slug = slug.replaceAll("^[^a-z0-9]+", "").replaceAll("[^a-z0-9]+$", "");
        if (slug.length() < 3) {
            slug = "org";
        }
        if (slug.length() == 1) {
            slug = slug + "org";
        }
        // Single char middle cases: pad
        if (!PATTERN.matcher(slug).matches() && slug.length() >= 2) {
            if (!Character.isLetterOrDigit(slug.charAt(0))) {
                slug = "a" + slug;
            }
            if (!Character.isLetterOrDigit(slug.charAt(slug.length() - 1))) {
                slug = slug + "0";
            }
            if (slug.length() > 40) {
                slug = slug.substring(0, 40);
            }
        }
        return slug;
    }

    private static String trimTrailingHyphen(String value) {
        return value.replaceAll("-+$", "");
    }
}
