package com.somimas.saas.provisioning;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvisioningCredentialRepository extends JpaRepository<ProvisioningCredential, Long> {

    Optional<ProvisioningCredential> findByOrganizationId(Long organizationId);

    void deleteByOrganizationId(Long organizationId);
}
