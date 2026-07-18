# Somimas Native Ubuntu VPS Deployment

Production runbook for deploying Somimas (Apache Fineract + SaaS control plane + Angular frontend) on **Ubuntu 24.04 LTS** with **Java 21**, **MariaDB 11.5+**, **Nginx**, **Certbot**, and **UFW**.

## Architecture

```
Internet ──► Nginx :443 (microfinance.softecki.com)
                 ├── /                      → /var/www/somimas/current  (Angular SPA)
                 ├── /fineract-provider/    → 127.0.0.1:8080  (Fineract, SSL off)
                 └── /saas-api/             → 127.0.0.1:8090  (Control plane)

MariaDB :3306 (localhost)
  ├── somimas_control      — SaaS identities, billing, provisioning
  ├── fineract_tenants     — Fineract tenant registry
  ├── fineract_default     — Default Fineract tenant
  └── somimas_<slug>       — Dynamically provisioned tenant DBs
```

| Path | Purpose |
|------|---------|
| `/opt/somimas/releases/<id>/` | Backend JAR releases |
| `/opt/somimas/current` | Symlink → active backend release |
| `/var/www/somimas/releases/<id>/` | Frontend releases |
| `/var/www/somimas/current` | Symlink → active frontend |
| `/var/lib/somimas/fineract-content` | Fineract document storage |
| `/var/lib/somimas/receipts` | Bank deposit receipt uploads |
| `/etc/somimas/` | Runtime environment files |
| `/var/backups/somimas/` | Logical backups |
| `/opt/somimas/scripts/` | Installed deploy/backup scripts |

---

## Prerequisites

- Ubuntu 24.04 VPS (≥ 4 GB RAM, 2 vCPU recommended for staging)
- DNS `A` record: `microfinance.softecki.com` → server public IP
- SSH access as a sudo-capable user
- Built artifacts from your workstation:
  - `fineract-provider-*.jar` from `Somimas-main/`
  - `saas-control-plane-*.jar` from `Somimas-main/`
  - Angular `dist/web-app/` from `Somimas_frontend-main/`

---

## 1. Build artifacts (workstation)

From the repository root (`Somimas-main/` parent):

```bash
# Backend JARs (Java 21). The Fineract bootJar includes the Somimas SaaS bridge.
cd Somimas-main
./gradlew :fineract-provider:bootJar :saas-control-plane:bootJar

FINERACT_JAR="$(ls -1 fineract-provider/build/libs/fineract-provider-*.jar | grep -v plain | tail -1)"
CONTROL_JAR="$(ls -1 saas-control-plane/build/libs/saas-control-plane-*.jar | grep -v plain | tail -1)"

# Frontend
cd ../Somimas_frontend-main
npm ci
npm run build
FRONTEND_DIR="$(pwd)/dist/web-app/browser"
```

Copy the kit and artifacts to the server:

```bash
rsync -av deployment/native-vps/ user@microfinance.softecki.com:/tmp/somimas-native-vps/
rsync -av "$FINERACT_JAR" "$CONTROL_JAR" user@microfinance.softecki.com:/tmp/artifacts/
rsync -av "$FRONTEND_DIR/" user@microfinance.softecki.com:/tmp/artifacts/web-app/
```

---

## 2. Bootstrap the server (once)

SSH to the VPS and run:

```bash
cd /tmp/somimas-native-vps
sudo APP_DOMAIN=microfinance.softecki.com bash ./bootstrap-ubuntu.sh
```

The bootstrap script is **idempotent** — safe to re-run after kit updates.

### Configure secrets

1. Edit database passwords in `/etc/somimas/mariadb/bootstrap.sql` (replace all `CHANGE_ME_*`).
2. Apply bootstrap:

```bash
sudo mariadb < /etc/somimas/mariadb/bootstrap.sql
```

3. Mirror the same passwords in env files:

```bash
sudo nano /etc/somimas/fineract.env
sudo nano /etc/somimas/control-plane.env
```

4. Optional backup credentials:

```bash
sudo tee /etc/somimas/backup.env <<'EOF'
DB_USER=somimas_backup
DB_PASSWORD=<same as bootstrap.sql>
EOF
sudo chmod 640 /etc/somimas/backup.env
```

### Initialize Fineract schema (first deploy only)

After databases exist, run Fineract migrations once:

```bash
cd /tmp/Somimas-main   # or upload source
./gradlew createDB -PdbName=fineract_tenants
./gradlew createDB -PdbName=fineract_default
# Control plane Liquibase runs automatically on first start
```

### TLS certificate

```bash
sudo APP_DOMAIN=microfinance.softecki.com \
  ADMIN_EMAIL=admin@softecki.com \
  /opt/somimas/scripts/configure-nginx.sh
sudo certbot renew --dry-run
```

`configure-nginx.sh` first installs an HTTP-only ACME site, obtains the
certificate, then writes and enables `/etc/nginx/sites-available/somimas.conf`.

---

## 3. Deploy a release

```bash
sudo /opt/somimas/scripts/deploy.sh \
  --fineract-jar /tmp/artifacts/fineract-provider-*.jar \
  --control-plane-jar /tmp/artifacts/saas-control-plane-*.jar \
  --frontend-dir /tmp/artifacts/web-app \
  --app-domain microfinance.softecki.com
```

The deploy script:

1. Creates `/opt/somimas/releases/<timestamp>/` and `/var/www/somimas/releases/<timestamp>/`
2. Copies JARs and frontend files
3. Generates `/var/www/somimas/current/assets/env.js` from `env.template.js` via `envsubst`
4. Updates `current` symlinks
5. Restarts `fineract`, `somimas-control-plane`, reloads nginx
6. Waits for actuator health on ports 8080 and 8090

### Recommended: deploy after `git pull`

The workspace is intended to be one repository containing both applications.
If it has not yet been initialized, run these commands from the outer
`Somimas-main` directory on the development machine:

```bash
git init
git add Somimas-main Somimas_frontend-main deployment
git commit -m "Add Somimas multitenant SaaS deployment"
git branch -M main
git remote add origin YOUR_GIT_REPOSITORY_URL
git push -u origin main
```

Clone the repository once on the server. The repository root must contain
`Somimas-main`, `Somimas_frontend-main`, and `deployment`:

```bash
sudo apt install -y git
sudo mkdir -p /srv/somimas
sudo chown "$USER":"$USER" /srv/somimas
git clone YOUR_GIT_REPOSITORY_URL /srv/somimas/app
cd /srv/somimas/app

bash ./deployment/native-vps/deploy-from-git.sh \
  --repo-dir /srv/somimas/app \
  --branch main \
  --app-domain microfinance.softecki.com
```

For later releases, run the same `deploy-from-git.sh` command. It performs
`git pull --ff-only`, builds both Java services and Angular, updates systemd
files, validates Nginx, deploys an atomic release, and runs both health checks.

---

## 4. Rollback

```bash
# Revert to previous release (recorded at deploy time)
sudo /opt/somimas/scripts/rollback.sh

# Or target a specific release
sudo /opt/somimas/scripts/rollback.sh --release-id 20260718T141500Z

# List available releases
sudo /opt/somimas/scripts/rollback.sh --list
```

---

## 5. Backup & restore

### Manual backup

```bash
sudo /opt/somimas/scripts/backup.sh
# Output: /var/backups/somimas/<timestamp>/
```

Backups include:

- `somimas_control`, `fineract_tenants`, `fineract_default`
- All dynamic tenant databases matching `somimas_%`
- `/var/lib/somimas/fineract-content` and `receipts`

Nightly cron: `/etc/cron.d/somimas-backup` (02:15 UTC).

### Restore (staging first)

```bash
sudo systemctl stop somimas-control-plane fineract
sudo /opt/somimas/scripts/restore.sh --from /var/backups/somimas/20260718T021500Z
```

---

## 6. Service management

```bash
sudo systemctl status fineract somimas-control-plane nginx mariadb
sudo journalctl -u fineract -f
sudo journalctl -u somimas-control-plane -f
tail -f /var/log/somimas/fineract.log
tail -f /var/log/nginx/somimas-access.log
```

| Service | Port | Health endpoint |
|---------|------|-----------------|
| Fineract | 127.0.0.1:8080 | `/fineract-provider/actuator/health` |
| Control plane | 127.0.0.1:8090 | `/saas-api/actuator/health` |
| Nginx | 0.0.0.0:443 | `https://microfinance.softecki.com/` |

---

## 7. Configuration reference

| File | Description |
|------|-------------|
| `etc/somimas/fineract.env.example` | Fineract JVM environment |
| `etc/somimas/control-plane.env.example` | Control plane + billing |
| `etc/mariadb/bootstrap.sql` | DB + user creation |
| `etc/mariadb/99-somimas.cnf` | MariaDB tuning |
| `etc/nginx/sites-available/somimas.conf` | Reverse proxy + SPA |
| `etc/systemd/fineract.service` | Fineract systemd unit |
| `etc/systemd/somimas-control-plane.service` | Control plane unit |
| `etc/logrotate.d/somimas` | Log rotation |
| `etc/cron.d/somimas-backup` | Nightly backup schedule |

Installed to `/etc/` by `bootstrap-ubuntu.sh`.

---

## 8. Phased rollout & verification

- **[docs/ROLLOUT_GATES.md](docs/ROLLOUT_GATES.md)** — Phase 0–4 feature flags and exit criteria
- **[docs/VERIFICATION_CHECKLIST.md](docs/VERIFICATION_CHECKLIST.md)** — Staging smoke tests before production

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Fineract won't start | DB password mismatch | Check `/etc/somimas/fineract.env` vs MariaDB users |
| 502 on `/fineract-provider/` | Fineract not listening | `systemctl status fineract`; check port 8080 |
| 502 on `/saas-api/` | Control plane down | `journalctl -u somimas-control-plane` |
| Blank Angular page | Missing `env.js` | Re-run `deploy.sh`; check `/assets/env.js` |
| Certbot fails | DNS not propagated | Verify `dig microfinance.softecki.com` |
| Backup fails | `somimas_backup` password | Configure `/etc/somimas/backup.env` |

---

## Kit contents

```
native-vps/
├── README.md                 ← this runbook
├── bootstrap-ubuntu.sh       ← first-time server setup
├── deploy.sh                 ← release deployment
├── rollback.sh               ← symlink rollback
├── backup.sh                 ← logical backup
├── restore.sh                ← restore from backup
├── etc/                      ← templates installed to /etc
└── docs/
    ├── ROLLOUT_GATES.md
    └── VERIFICATION_CHECKLIST.md
```

All shell scripts use `set -euo pipefail` and are installed executable to `/opt/somimas/scripts/` during bootstrap.
