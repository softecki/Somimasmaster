package com.somimas.saas.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.somimas.saas.organization.Organization;
import com.somimas.saas.organization.OrganizationRepository;
import com.somimas.saas.web.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlugValidatorTest {

    @Mock
    private OrganizationRepository organizationRepository;

    private SlugValidator slugValidator;

    @BeforeEach
    void setUp() {
        slugValidator = new SlugValidator(organizationRepository);
    }

    @Test
    void normalizeProducesBridgeCompatibleSlug() {
        assertEquals("acme-microfinance", slugValidator.normalize("Acme Microfinance!!!"));
    }

    @Test
    void generateUniqueSlugAppendsSuffixOnCollision() {
        when(organizationRepository.findBySlug("acme-mfi")).thenReturn(Optional.of(new Organization()));
        when(organizationRepository.findBySlug("acme-mfi-2")).thenReturn(Optional.empty());
        assertEquals("acme-mfi-2", slugValidator.generateUniqueSlug("Acme MFI"));
    }

    @Test
    void reservedSlugRejected() {
        ApiException ex = assertThrows(ApiException.class, () -> slugValidator.validate("default"));
        assertTrue(ex.getMessage().toLowerCase().contains("reserved"));
    }

    @Test
    void invalidPatternRejected() {
        assertThrows(ApiException.class, () -> slugValidator.validate("-bad-"));
    }
}
