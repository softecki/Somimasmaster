package com.somimas.fineract.saas.provisioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.somimas.fineract.saas.provisioning.dto.AdminSeedRequest;
import com.somimas.fineract.saas.provisioning.dto.AdminSeedResult;
import org.junit.jupiter.api.Test;

class TenantAdminSeedServiceTest {

    private final TenantAdminSeedService service = new TenantAdminSeedService();

    @Test
    void rejectsMissingFields() {
        AdminSeedRequest request = new AdminSeedRequest();
        request.setUsername("owner@example.com");
        AdminSeedResult result = service.seedAdmin("acme-mfi", request);
        assertEquals(AdminSeedResult.Status.FAILED, result.getStatus());
        assertNull(result.getAppUserId());
    }

    @Test
    void rejectsNonDelegatingPasswordHash() {
        AdminSeedRequest request = new AdminSeedRequest();
        request.setUsername("owner@example.com");
        request.setEmail("owner@example.com");
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setPasswordHash("$2a$10$notdelegating");
        AdminSeedResult result = service.seedAdmin("acme-mfi", request);
        assertEquals(AdminSeedResult.Status.FAILED, result.getStatus());
    }

    @Test
    void rejectsInvalidTenantIdentifier() {
        AdminSeedRequest request = new AdminSeedRequest();
        request.setUsername("owner@example.com");
        request.setEmail("owner@example.com");
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setPasswordHash("{bcrypt}$2a$10$abcdefghijklmnopqrstuv");
        try {
            service.seedAdmin("-bad-", request);
        } catch (IllegalArgumentException ex) {
            assertEquals(true, ex.getMessage().contains("Invalid tenant identifier"));
        }
    }
}
