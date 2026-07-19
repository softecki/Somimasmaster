# Somimas Microfinance SaaS

Somimas is a multitenant microfinance SaaS platform built on Apache Fineract and the Mifos X Angular web application. It adds a SaaS control plane for self-service registration, tenant provisioning, subscriptions, billing, and platform administration.

Production URL: `https://microfinance.softecki.com`

## Contents

- [Architecture](#architecture)
- [Repository structure](#repository-structure)
- [Main features](#main-features)
- [SaaS APIs and frontend routes](#saas-apis-and-frontend-routes)
- [Requirements](#requirements)
- [Local development](#local-development)
- [Production deployment](#production-deployment)
- [Configuration and secrets](#configuration-and-secrets)
- [Database layout and tenancy](#database-layout-and-tenancy)
- [Authentication](#authentication)
- [Operating the services](#operating-the-services)
- [Backups, restore, and rollback](#backups-restore-and-rollback)
- [Troubleshooting](#troubleshooting)
- [Security and known limitations](#security-and-known-limitations)

## Architecture

```text
Browser
  |
  v
Nginx :443 (TLS)
  |-- /                         -> Angular SPA
  |-- /fineract-provider/       -> Fineract on 127.0.0.1:8080
  `-- /saas-api/                -> SaaS control plane on 127.0.0.1:8090

MariaDB :3306 (loopback only)
  |-- fineract_tenants          -> Fineract tenant registry
  |-- fineract_default          -> default Fineract tenant
  |-- somimas_control           -> SaaS users, organizations, plans, billing
  `-- somimas_<tenant>          -> provisioned tenant databases
```

The public edge uses same-origin routing. The Angular application does not contact ports 8080 or 8090 directly; it calls `/fineract-provider/` and `/saas-api/` through Nginx.

### Components

| Component | Technology | Purpose |
|---|---|---|
| Core banking | Apache Fineract, Java 21, Spring Boot | Clients, loans, savings, accounting, users, and tenant data |
| SaaS bridge | Custom Fineract modules | Internal tenant provisioning and access enforcement |
| Control plane | Java 21, Spring Boot | Signup, organizations, subscriptions, billing, tenant lifecycle |
| Web application | Angular and Angular Material | Mifos operations UI plus Somimas SaaS pages |
| Database | MariaDB 11.5.2+ | Tenant registry, tenant schemas, and control-plane data |
| Edge | Nginx and Let's Encrypt | TLS, reverse proxy, static SPA, rate limiting |
| Process manager | systemd | Starts and restarts both Java services |

## Repository structure

```text
.
|-- Somimas-main/                 # Fineract backend source
|   |-- custom/somimas/saas/      # Fineract SaaS bridge modules
|   `-- saas-control-plane/       # SaaS control-plane service
|-- Somimas_frontend-main/        # Angular/Mifos web application
`-- deployment/native-vps/        # Ubuntu deployment and operations kit
```

Important deployment files:

| File | Purpose |
|---|---|
| `deployment/native-vps/bootstrap-ubuntu.sh` | One-time Ubuntu setup |
| `deployment/native-vps/deploy-from-git.sh` | Pull, build, and deploy from Git |
| `deployment/native-vps/deploy.sh` | Install already-built artifacts |
| `deployment/native-vps/configure-nginx.sh` | Configure Nginx and TLS |
| `deployment/native-vps/rollback.sh` | Switch to an earlier release |
| `deployment/native-vps/backup.sh` | Back up databases and uploaded content |
| `deployment/native-vps/restore.sh` | Restore a backup |
| `deployment/native-vps/etc/` | Versioned configuration templates |

The detailed VPS runbook is also available at [deployment/native-vps/README.md](deployment/native-vps/README.md).

## Main features

- Standard Fineract/Mifos microfinance operations.
- Multitenant database isolation.
- SaaS registration and organization lifecycle.
- Automatic tenant slug generation and dedicated Fineract database provisioning.
- Owner administrator seeding so the registration email/password can log into the new tenant.
- Trial and subscription configuration.
- Tenant provisioning through an internal authenticated bridge.
- Tenant access states such as `ACTIVE` and `SUSPENDED`.
- Platform-administrator configuration.
- Flutterwave configuration hooks.
- Manual bank-deposit receipt storage.
- Atomic backend/frontend releases with symlink switching.
- Automated backups, restore, log rotation, TLS, firewall rules, and service health checks.

## SaaS APIs and frontend routes

### Control plane (`/saas-api`)

Public:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/public/signup` | Register a SaaS user |
| `POST` | `/public/login` | Issue a SaaS JWT |
| `GET` | `/public/plans` | List published plans |

Authenticated organization and billing:

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/organizations` | List organizations for the current user |
| `GET` | `/organizations/{id}` | Organization details |
| `GET` | `/organizations/{id}/provisioning` | Tenant provisioning status |
| `GET` | `/organizations/{id}/subscription` | Current subscription |
| `GET` | `/organizations/{id}/invoices` | Invoices |
| `POST` | `/organizations/{id}/payments/checkout-session` | Start a payment session |
| `POST` | `/organizations/{id}/bank-deposits` | Submit a bank-deposit receipt |

Webhooks and platform administration:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/webhooks/flutterwave` | Flutterwave webhook |
| `/platform/**` | organization list, suspend/reactivate, pending-deposit review | Platform-admin operations |

### Fineract SaaS bridge (`/fineract-provider/internal/saas`)

Protected by `X-Somimas-Bridge-Token` and blocked from the public internet by Nginx:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/internal/saas/tenants` | Provision a tenant DB and register it |
| `GET` | `/internal/saas/tenants/status/{identifier}` | Provisioning status |
| `POST` | `/internal/saas/tenants/{identifier}/admin` | Seed the organization owner as a Fineract admin |
| `POST` | `/internal/saas/tenants/{identifier}/access-state` | Set `ACTIVE` or `SUSPENDED` |

Provisioning creates `somimas_<slug>`, a tenant DB user, a Fineract registry row, encrypted credentials, Liquibase migrations, and an owner `m_appuser` (email as username) using a Fineract-compatible password hash derived at signup. Suspended tenants are denied operational API access by the bridge filter.

### Organization registration lifecycle

1. User opens `/#/login` and clicks **Register your organization**, or goes to `/#/saas/signup`.
2. Signup collects first name, last name, email, password, and organization name. The control plane generates a unique tenant slug from the organization name (no client-supplied slug).
3. The control plane creates the SaaS identity, organization, trial subscription, provisioning job, and an ephemeral Fineract-compatible password hash.
4. The provisioning worker calls the Fineract bridge to create the tenant database, run Liquibase, and seed the owner administrator.
5. The provisioning UI shows step progress. When complete, **Continue to tenant login** selects the new tenant and opens `/#/login`.
6. The owner signs in with their **email** as the Fineract username and the **same registration password**.

SaaS Cloud login (`/#/saas/login`) and Fineract tenant login (`/#/login`) remain separate sessions. Selecting an organization only sets the tenant identifier; it does not create a Fineract session.

### Frontend SaaS routes (hash routing)

| Route | Purpose |
|---|---|
| `/#/saas` | Landing and plans |
| `/#/saas/signup` | SaaS registration |
| `/#/saas/login` | SaaS login |
| `/#/saas/organizations` | Organization list |
| `/#/saas/organizations/:id/billing` | Invoices and payments |
| `/#/saas/organizations/:id/provisioning` | Provisioning progress |
| `/#/saas/platform` | Platform-admin console |

Operational microfinance login remains the standard Mifos/Fineract login page and uses the Fineract tenant API, not the SaaS JWT.

## Requirements

### Development

- Git
- Java 21
- Node.js 20+ (Node.js 22 is used by the VPS bootstrap)
- npm
- MariaDB 11.5.2+

### Production

- Ubuntu 24.04 LTS
- At least 4 GB RAM and 2 vCPU recommended
- At least 4 GB swap recommended on small VPS instances
- DNS `A` record pointing the application domain to the VPS
- Ports 22, 80, and 443 open
- A sudo-capable deployment user

The deployment script limits the Gradle build to a 2 GB heap, two workers, and non-parallel execution. Override the heap only when the VPS has sufficient memory:

```bash
GRADLE_XMX=3g bash deployment/native-vps/deploy-from-git.sh ...
```

## Local development

### Build the backend

From the repository root:

```bash
cd Somimas-main

# Linux/macOS
./gradlew :fineract-provider:bootJar :saas-control-plane:bootJar

# Windows PowerShell
.\gradlew.bat :fineract-provider:bootJar :saas-control-plane:bootJar
```

Artifacts are generated under:

```text
Somimas-main/fineract-provider/build/libs/
Somimas-main/saas-control-plane/build/libs/
```

### Build or run the frontend

```bash
cd Somimas_frontend-main
npm install
npm start
```

Production build:

```bash
npm ci
npm run build
```

Output is written to `Somimas_frontend-main/dist/web-app/` (normally with browser assets in `browser/`).

### Frontend runtime configuration

Production values are generated at deployment time from:

```text
Somimas_frontend-main/src/assets/env.template.js
```

The generated file is:

```text
/var/www/somimas/current/assets/env.js
```

Do not cache `env.js`; it contains runtime endpoint and tenant configuration.

## Production deployment

The production repository location used by this project is:

```text
/srv/somimas/app
```

### 1. Prepare DNS

Create an `A` record:

```text
microfinance.softecki.com -> VPS_PUBLIC_IP
```

Confirm DNS before requesting the certificate:

```bash
dig +short microfinance.softecki.com
```

### 2. Clone the repository

```bash
sudo apt update
sudo apt install -y git
sudo mkdir -p /srv/somimas
sudo chown "$USER":"$USER" /srv/somimas
git clone YOUR_GIT_REPOSITORY_URL /srv/somimas/app
cd /srv/somimas/app
```

### 3. Bootstrap Ubuntu (once)

```bash
cd /srv/somimas/app
sudo APP_DOMAIN=microfinance.softecki.com \
  bash deployment/native-vps/bootstrap-ubuntu.sh
```

The idempotent bootstrap installs:

- Java 21
- MariaDB 11.8 LTS
- Node.js 22
- Nginx and Certbot
- UFW rules for SSH and Nginx
- system user `somimas`
- systemd units, log rotation, backup cron, and directory permissions

### 4. Configure secrets

Edit the installed files:

```bash
sudo nano /etc/somimas/mariadb/bootstrap.sql
sudo nano /etc/somimas/fineract.env
sudo nano /etc/somimas/control-plane.env
```

Use the same database passwords consistently across the SQL and environment files. The bridge token must match in both Java services. The JWT secret should be a separate random secret.

Apply the database bootstrap:

```bash
sudo mariadb < /etc/somimas/mariadb/bootstrap.sql
```

Protect environment files:

```bash
sudo chown root:somimas /etc/somimas/fineract.env /etc/somimas/control-plane.env
sudo chmod 640 /etc/somimas/fineract.env /etc/somimas/control-plane.env
```

### 5. Configure TLS and Nginx

```bash
sudo APP_DOMAIN=microfinance.softecki.com \
  ADMIN_EMAIL=YOUR_ADMIN_EMAIL \
  /opt/somimas/scripts/configure-nginx.sh

sudo certbot renew --dry-run
```

Nginx serves the Angular SPA and proxies both backend services. Internal Fineract provisioning endpoints are explicitly blocked at the public edge.

### 6. Deploy from Git

This is the standard deployment command for the project:

```bash
cd /srv/somimas/app
git pull

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

bash deployment/native-vps/deploy-from-git.sh \
  --repo-dir /srv/somimas/app \
  --branch main \
  --app-domain microfinance.softecki.com
```

`deploy-from-git.sh` performs:

1. `git fetch`, checkout, and `git pull --ff-only`.
2. Backend builds for Fineract and the control plane.
3. Angular dependency installation and production build.
4. Installation of current deployment/systemd/Nginx files.
5. Timestamped release creation.
6. Runtime `env.js` generation.
7. Atomic `current` symlink switching.
8. Service restarts and Nginx reload.
9. Health checks for both Java services.

Health-check failures are logged with recent service/application logs but do not automatically roll back the release. Rollback remains an explicit operator decision.

### 7. Fast incremental deployments

For routine updates, rebuild only the part that changed. Both fast-path scripts pull `origin/main` automatically.

Frontend-only changes (UI, branding, translations, themes, and styles):

```bash
cd /srv/somimas/app
bash deployment/native-vps/deploy-frontend.sh \
  --repo-dir /srv/somimas/app \
  --branch main \
  --app-domain microfinance.softecki.com
```

This builds and switches only the Angular release, skips `npm ci` when dependencies are unchanged, reloads Nginx, and does not rebuild or restart either Java service.

Backend-only changes (Fineract, SaaS bridge, or control plane):

```bash
cd /srv/somimas/app
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

bash deployment/native-vps/deploy-backend.sh \
  --repo-dir /srv/somimas/app \
  --branch main \
  --app-domain microfinance.softecki.com
```

This uses the Gradle build cache, deploys only the Java JARs, restarts the Java services, and leaves the frontend unchanged.

Use the full `deploy-from-git.sh` command when:

- Both frontend and backend changed.
- Dependencies or lockfiles changed.
- Nginx, systemd, or deployment templates changed.
- Performing the first deployment after adding these fast-path scripts.

Pass `--no-pull` to either fast-path script only when the desired commit is already checked out on the VPS. After frontend changes, use `Ctrl+Shift+R` to bypass cached browser assets.

### 8. Deploy prebuilt artifacts

```bash
sudo /opt/somimas/scripts/deploy.sh \
  --fineract-jar /path/to/fineract-provider.jar \
  --control-plane-jar /path/to/saas-control-plane.jar \
  --frontend-dir /path/to/dist/web-app \
  --app-domain microfinance.softecki.com
```

Use `--skip-health-check` only for diagnosis, not routine production deployment.

## Configuration and secrets

Runtime configuration is under `/etc/somimas/`:

| File | Purpose |
|---|---|
| `fineract.env` | Fineract server, registry DB, default tenant, bridge, JVM |
| `control-plane.env` | Control DB, auth, billing, bridge, JVM |
| `fineract.jvm.opts` | Optional Fineract JVM override |
| `control-plane.jvm.opts` | Optional control-plane JVM override |
| `backup.env` | Backup/restore database credentials |
| `mariadb/bootstrap.sql` | Database and user initialization |

Never commit live secrets. Replace example values before production use and rotate any secret that has previously been committed.

Generate strong random values:

```bash
openssl rand -hex 32   # bridge token or JWT secret
openssl rand -base64 36
```

Recommended JVM settings for a small VPS:

```text
Fineract:      -Xms256m -Xmx1024m -XX:+UseG1GC -XX:MaxMetaspaceSize=256m
Control plane: -Xms256m -Xmx768m  -XX:+UseG1GC
```

## Database layout and tenancy

| Database | Owner/use |
|---|---|
| `fineract_tenants` | Tenant identifiers and encrypted tenant DB connection details |
| `fineract_default` | Default operational tenant |
| `somimas_control` | SaaS control-plane records |
| `somimas_<slug>` | Dynamically provisioned operational tenant |

Fineract tenant requests use the `Fineract-Platform-TenantId` header. The production frontend defaults to tenant identifier `default`.

### Tenant master password

The tenant DB password stored in `fineract_tenants.tenant_server_connections` is encrypted using Fineract's master password. Its `master_password_hash` must match:

```text
FINERACT_DEFAULT_TENANTDB_MASTER_PASSWORD
```

Changing the master password without re-encrypting tenant connection rows prevents Fineract from starting with:

```text
IllegalArgumentException: Invalid master password
```

To repair a mismatched default-tenant connection, stop Fineract, generate values using the live environment, update the row, and restart:

```bash
sudo systemctl stop fineract.service

MASTER=$(sudo grep -oP '^FINERACT_DEFAULT_TENANTDB_MASTER_PASSWORD=\K.*' /etc/somimas/fineract.env)
DBPASS=$(sudo grep -oP '^FINERACT_DEFAULT_TENANTDB_PASSWORD=\K.*' /etc/somimas/fineract.env)

OUT=$(java -cp /opt/somimas/current/fineract-provider.jar \
  -Dloader.main=org.apache.fineract.infrastructure.core.service.database.DatabasePasswordEncryptor \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  "$MASTER" "$DBPASS")

printf '%s\n' "$OUT"
ENC=$(printf '%s\n' "$OUT" | grep -oP 'encrypted password:\s*\K\S+')
HASH=$(printf '%s\n' "$OUT" | grep -oP 'master password hash is:\s*\K\S+')

test -n "$ENC" && test -n "$HASH" || {
  echo "Could not parse generated values; do not update the database"
  exit 1
}

sudo mariadb -e "
UPDATE fineract_tenants.tenant_server_connections
SET schema_password='${ENC}', master_password_hash='${HASH}'
WHERE schema_name='fineract_default';"

sudo systemctl start fineract.service
sudo tail -f /var/log/somimas/fineract.log
```

Back up `fineract_tenants` before changing connection records. Do not set `schema_password` to an empty value.

## Authentication

Somimas currently has two authentication domains:

1. **Fineract/Mifos login** for operational microfinance users.
2. **SaaS control-plane login** for registration, organization, billing, and platform administration.

The standard Fineract development seed commonly creates:

```text
Username: mifos
Password: password
Tenant:   default
```

These credentials exist only if the default tenant was seeded accordingly. Do not rely on default credentials in production. Confirm users with:

```bash
sudo mariadb -e \
  "SELECT id, username, enabled, nonlocked, firsttime_login_remaining FROM fineract_default.m_appuser;"
```

Test authentication directly:

```bash
curl -sS -X POST \
  "https://microfinance.softecki.com/fineract-provider/api/v1/authentication" \
  -H "Content-Type: application/json" \
  -H "Fineract-Platform-TenantId: default" \
  -d '{"username":"mifos","password":"password"}'
```

A `401` means the backend is reachable but the credentials, tenant header, account state, or seeded user is incorrect. A `502` means Nginx cannot reach Fineract.

## Operating the services

### Status

```bash
sudo systemctl status fineract.service --no-pager
sudo systemctl status somimas-control-plane.service --no-pager
sudo systemctl status nginx mariadb --no-pager

systemctl is-active fineract.service
systemctl is-active somimas-control-plane.service
```

### Start, stop, and restart

```bash
sudo systemctl restart fineract.service
sudo systemctl restart somimas-control-plane.service
sudo systemctl stop fineract.service
sudo systemctl start fineract.service
```

### Health checks

```bash
curl -i http://127.0.0.1:8080/fineract-provider/actuator/health
curl -i http://127.0.0.1:8090/saas-api/actuator/health
curl -I https://microfinance.softecki.com/
```

During first startup, Fineract may be active in systemd while still running tenant migrations. Port 8080 will not answer until startup advances far enough.

### Logs

Application output is redirected to files, so Java exceptions may not appear in `journalctl`:

```bash
sudo tail -f /var/log/somimas/fineract.log
sudo tail -f /var/log/somimas/control-plane.log
sudo tail -f /var/log/nginx/somimas-error.log
sudo tail -f /var/log/nginx/somimas-access.log
```

Systemd lifecycle events:

```bash
sudo journalctl -u fineract.service -n 100 --no-pager
sudo journalctl -u somimas-control-plane.service -n 100 --no-pager
```

### Ports

| Service | Listen address | Public |
|---|---|---|
| Nginx HTTP | `0.0.0.0:80` | Yes, redirects/ACME |
| Nginx HTTPS | `0.0.0.0:443` | Yes |
| Fineract | `127.0.0.1:8080` | No, proxied |
| Control plane | `127.0.0.1:8090` | No, proxied |
| MariaDB | local | No |

### Release locations

| Path | Purpose |
|---|---|
| `/opt/somimas/releases/<release-id>/` | Backend JAR release |
| `/opt/somimas/current` | Active backend symlink |
| `/var/www/somimas/releases/<release-id>/` | Frontend release |
| `/var/www/somimas/current` | Active frontend symlink |
| `/var/lib/somimas/fineract-content` | Fineract documents |
| `/var/lib/somimas/receipts` | Deposit receipts |
| `/var/backups/somimas` | Backups |

## Backups, restore, and rollback

### Backup

Configure `/etc/somimas/backup.env`:

```bash
sudo tee /etc/somimas/backup.env >/dev/null <<'EOF'
DB_USER=somimas_backup
DB_PASSWORD=REPLACE_WITH_BACKUP_PASSWORD
EOF
sudo chown root:somimas /etc/somimas/backup.env
sudo chmod 640 /etc/somimas/backup.env
```

Run:

```bash
sudo /opt/somimas/scripts/backup.sh
sudo /opt/somimas/scripts/backup.sh --retention-days 30
```

Backups include static databases, all `somimas_%` tenant databases, Fineract content, receipts, checksums, and a manifest. The installed cron schedule runs nightly.

### Restore

Restore is destructive and should be tested on staging first:

```bash
sudo /opt/somimas/scripts/restore.sh \
  --from /var/backups/somimas/RELEASE_TIMESTAMP
```

Skip content archives when required:

```bash
sudo /opt/somimas/scripts/restore.sh \
  --from /var/backups/somimas/RELEASE_TIMESTAMP \
  --skip-content
```

### Rollback application artifacts

```bash
sudo /opt/somimas/scripts/rollback.sh --list
sudo /opt/somimas/scripts/rollback.sh
sudo /opt/somimas/scripts/rollback.sh --release-id YYYYMMDDTHHMMSSZ
```

Rollback changes application symlinks only. It does not reverse database migrations. Always keep a compatible backup before upgrades.

## Troubleshooting

### Gradle daemon disappeared

If the daemon log shows `-Xmx12g`, the process was probably killed by the VPS OOM killer. Use the project deployment script, which overrides the heap:

```bash
bash deployment/native-vps/deploy-from-git.sh \
  --repo-dir /srv/somimas/app \
  --branch main \
  --app-domain microfinance.softecki.com
```

Check memory and kernel events:

```bash
free -h
sudo dmesg -T | grep -Ei 'out of memory|killed process|oom'
```

Create swap once on a small VPS if none exists:

```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### Angular build prints `Killed`

`Killed` with no other error means the kernel OOM killer terminated `ng build` because the VPS ran out of memory while the Java services and MariaDB were running. Fix it the same way as the Gradle OOM:

1. Create swap once (commands above) if `swapon --show` prints nothing.
2. Re-run the deploy. The deploy scripts cap the Node heap at 2048 MB by default; raise it per run if the build still fails with a JavaScript heap error:

```bash
NODE_XMX_MB=3072 bash deployment/native-vps/deploy-frontend.sh \
  --repo-dir /srv/somimas/app \
  --branch main \
  --app-domain microfinance.softecki.com
```

### `502 Bad Gateway`

The SPA can load while a proxied Java service is down.

```bash
systemctl is-active fineract.service
curl -i http://127.0.0.1:8080/fineract-provider/actuator/health
sudo tail -n 120 /var/log/somimas/fineract.log
```

Use the equivalent commands for `somimas-control-plane.service` and port 8090.

### `401 Unauthenticated`

A 401 proves the request reached Fineract. Check:

- username/password;
- `Fineract-Platform-TenantId: default`;
- the user exists and is enabled/unlocked;
- frontend `/assets/env.js` contains the correct tenant and same-origin URL.

### `Invalid master password`

The tenant registry hash and runtime master password differ. Use the repair procedure in [Tenant master password](#tenant-master-password). Do not repeatedly restart Fineract without correcting the registry row.

### Service appears running but health fails

Fineract may still be applying Liquibase migrations. Watch:

```bash
sudo tail -f /var/log/somimas/fineract.log
```

If the systemd uptime repeatedly resets, the process is crash-looping; inspect the most recent `Caused by:` section in the application log.

### Nginx `protocol options redefined`

This warning means multiple enabled virtual hosts declare different protocol options for the same `0.0.0.0:443` listener. It does not make `nginx -t` fail, but the enabled site files should use a consistent `listen 443 ssl http2;` style.

List enabled sites:

```bash
sudo grep -R "listen .*443" /etc/nginx/sites-enabled/
sudo nginx -t
```

### Git says `nothing to commit, working tree clean`

This is success: every tracked change is already committed. If the branch is also up to date with `origin/main`, everything has already been pushed.

```bash
git status
git log -3 --oneline
git rev-parse HEAD
git rev-parse origin/main
```

### Useful diagnostic bundle

```bash
sudo systemctl status fineract.service somimas-control-plane.service --no-pager
free -h
sudo tail -n 100 /var/log/somimas/fineract.log
sudo tail -n 100 /var/log/somimas/control-plane.log
curl -i http://127.0.0.1:8080/fineract-provider/actuator/health
curl -i http://127.0.0.1:8090/saas-api/actuator/health
sudo nginx -t
```

## Security and known limitations

### Security baseline

- Rotate all database passwords, bridge tokens, and JWT secrets that have appeared in Git history or terminal transcripts.
- Never expose ports 8080, 8090, or 3306 publicly.
- Keep `/etc/somimas/*.env` readable only by `root` and the `somimas` group.
- Replace or disable default Fineract credentials before production use.
- Test backups by restoring them into staging.
- Flutterwave keys are optional until payment integration is enabled; never commit live keys.

### Current product limitations

- SaaS control-plane identities and Fineract operational users remain separate sessions (shared password is intentional; there is no SSO token bridge yet).
- Every new tenant Liquibase run still seeds the default `mifos` / `password` superuser. This implementation intentionally leaves that account enabled per product decision — treat it as a production risk and rotate or disable it before going live.
- Entitlement enforcement currently defaults to report-only mode and uses a small hard-coded route allowlist.
- Flutterwave checkout currently constructs a hosted URL locally and does not fully create or verify checkout sessions through Flutterwave's API until that integration is finished.
- Payment webhook matching is intentionally conservative and should be hardened further before high-volume billing.
- An application rollback does not undo database migrations.
- Current SaaS provisioning SQL assumes MariaDB; PostgreSQL is not the production target for the bridge.
- Existing SaaS owners still cannot create a second organization through the signup form (email uniqueness); that remains a follow-up.

## Verification

After deployment, complete:

- [Deployment verification checklist](deployment/native-vps/docs/VERIFICATION_CHECKLIST.md)
- [Rollout gates](deployment/native-vps/docs/ROLLOUT_GATES.md)

Minimum smoke test:

```bash
curl -fsS http://127.0.0.1:8080/fineract-provider/actuator/health
curl -fsS http://127.0.0.1:8090/saas-api/actuator/health
curl -fsSI https://microfinance.softecki.com/
```

## License and upstream projects

This repository incorporates and extends:

- [Apache Fineract](https://fineract.apache.org/)
- [Mifos X Web App](https://github.com/openMF/web-app)

Preserve the applicable upstream license and notice files when redistributing modified components.
