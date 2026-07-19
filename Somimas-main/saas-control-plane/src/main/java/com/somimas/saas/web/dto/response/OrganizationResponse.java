package com.somimas.saas.web.dto.response;

import java.time.LocalDateTime;

public record OrganizationResponse(Long id, String slug, String name, String status, String tenantIdentifier,
        LocalDateTime createdAt) {}
