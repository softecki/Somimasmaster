# Somimas Fineract SaaS Bridge

Custom Fineract modules that expose an internal provisioning bridge for multi-tenant SaaS deployments.

## Modules

| Module | Gradle path | Purpose |
|--------|-------------|---------|
| `service` | `:custom:somimas:saas:service` | Provisioning API, access control, identity mapping |
| `starter` | `:custom:somimas:saas:starter` | Spring Boot auto-configuration and Liquibase changelogs |

Modules are included automatically by `settings.gradle` under `custom/<company>/<category>/<module>`.

## Enable

Add the starter to your Fineract image (see `custom/docker/build.gradle`) and configure:

```properties
somimas.saas.enabled=true
somimas.saas.bridge.token=<shared-secret>
somimas.saas.encrypt-passwords=true
somimas.saas.db.admin-url=jdbc:mariadb://localhost:3306/
somimas.saas.db.admin-username=root
somimas.saas.db.admin-password=secret
somimas.saas.entitlement.report-only=true
```

## Internal provisioning API

All requests require header `X-Somimas-Bridge-Token` matching `somimas.saas.bridge.token`.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/internal/saas/tenants` | Create tenant DB, DB user, and tenant-store rows |
| `GET` | `/internal/saas/tenants/status/{identifier}` | Lookup tenant registration status |
| `POST` | `/internal/saas/tenants/{identifier}/access-state` | Set `ACTIVE` or `SUSPENDED` |

Tenant identifiers must match `^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$`. Database names are `somimas_<identifier>`.

## Post-provisioning: tenant schema upgrade

After `POST /internal/saas/tenants` succeeds, run Liquibase migrations for the new tenant database.
`TenantDatabaseUpgradeService` handles this in Fineract core.

From application code (e.g. a follow-up step in your control plane):

```java
@Autowired
private TenantDatabaseUpgradeService tenantDatabaseUpgradeService;

@Autowired
private TenantDetailsService tenantDetailsService;

@Autowired
private JdbcTenantDetailsService jdbcTenantDetailsService;

public void finalizeTenant(String identifier) throws LiquibaseException {
    // Clear cached tenant metadata so the new registry row is visible
    jdbcTenantDetailsService.evictTenantFromCache(identifier);

    FineractPlatformTenant tenant = tenantDetailsService.loadTenantById(identifier);
    tenantDatabaseUpgradeService.upgradeTenant(tenant);
}
```

Alternatively call `tenantDatabaseUpgradeService.upgradeTenantByIdentifier(identifier)`.

This applies standard Fineract tenant Liquibase changelogs plus custom changelogs (including
`0001_external_identity_mapping.xml`) to the newly created database.

## Tenant access enforcement

`TenantAccessFilter` blocks `/api/**` requests when `tenants.access_state` is not `ACTIVE`.
For tighter integration with Fineract security, register it immediately after
`TenantAwareBasicAuthenticationFilter` in `SecurityConfig` (the starter also registers a servlet filter).

## Database changes

| Changelog | Scope | Change |
|-----------|-------|--------|
| `fineract-provider/.../0011_tenant_access_state.xml` | tenant store | Adds `access_state`, `access_state_changed_at` to `tenants` |
| `starter/.../0001_external_identity_mapping.xml` | tenant DB | Creates `m_appuser_external_identity` |

## Password storage

When `somimas.saas.encrypt-passwords=true` (default), tenant DB passwords are encrypted with
`DatabasePasswordEncryptor.encrypt()` and `master_password_hash` is populated.

When `somimas.saas.encrypt-passwords=false`, passwords are stored in plaintext (development only).
