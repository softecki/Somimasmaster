#!/usr/bin/env bash
# bootstrap-ubuntu.sh — Idempotent first-time setup for Somimas on Ubuntu 24.04 LTS.
#
# Usage (as root on a fresh VPS):
#   curl -fsSL .../bootstrap-ubuntu.sh | sudo bash
#   # or
#   sudo APP_DOMAIN=microfinance.softecki.com ./bootstrap-ubuntu.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DOMAIN="${APP_DOMAIN:-microfinance.softecki.com}"
DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/somimas}"
KIT_VERSION="${KIT_VERSION:-native-vps}"

log() { printf '[bootstrap] %s\n' "$*"; }
die() { printf '[bootstrap] ERROR: %s\n' "$*" >&2; exit 1; }

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  die "Run as root: sudo $0"
fi

export DEBIAN_FRONTEND=noninteractive

log "Updating apt indexes..."
apt-get update -qq

log "Installing repository prerequisites..."
apt-get install -y --no-install-recommends \
  ca-certificates curl gnupg lsb-release apt-transport-https

# Extract the server version from `mariadb --version`, which varies by build:
#   "mariadb  Ver 15.1 Distrib 11.5.2-MariaDB, ..."
#   "mariadb from 11.8.8-MariaDB, client 15.2 for debian-linux-gnu ..."
mariadb_version() {
  mariadb --version 2>/dev/null | grep -oE '[0-9]+\.[0-9]+\.[0-9]+-MariaDB' | head -1 | sed 's/-MariaDB//'
}

# Ubuntu 24.04's default MariaDB is older than Fineract's supported 11.5.2+
# baseline. Install the MariaDB 11.8 LTS repository before installing server
# packages.
CURRENT_MARIADB_VERSION=""
if command -v mariadb >/dev/null 2>&1; then
  CURRENT_MARIADB_VERSION="$(mariadb_version)"
fi
if [[ -z "${CURRENT_MARIADB_VERSION}" ]] || \
   [[ "$(printf '%s\n' "11.5.2" "${CURRENT_MARIADB_VERSION}" | sort -V | head -1)" != "11.5.2" ]]; then
  log "Configuring the MariaDB 11.8 LTS repository..."
  curl -LsS https://r.mariadb.com/downloads/mariadb_repo_setup | \
    bash -s -- --mariadb-server-version=mariadb-11.8
  apt-get update -qq
fi

log "Installing Java 21, MariaDB 11.5+, Nginx, Certbot, UFW and build tools..."
apt-get install -y --no-install-recommends \
  openjdk-21-jdk-headless \
  mariadb-server mariadb-client \
  nginx \
  certbot python3-certbot-nginx \
  ufw gettext-base rsync jq git unzip

if ! command -v node >/dev/null 2>&1 || [[ "$(node -p 'Number(process.versions.node.split(`.`)[0])')" -lt 20 ]]; then
  log "Installing Node.js 22 LTS for the Angular production build..."
  curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
  apt-get install -y --no-install-recommends nodejs
fi

# Verify Java 21
java -version 2>&1 | head -1 | grep -q '21\.' || die "Java 21 not available after install"

# Verify MariaDB version >= 11.5.2
MARIADB_VER="$(mariadb_version)"
if [[ -z "${MARIADB_VER}" ]]; then
  log "WARNING: could not parse MariaDB version from: $(mariadb --version 2>/dev/null)"
  log "         Continuing; verify manually that the server is 11.5.2 or newer."
else
  log "MariaDB version: ${MARIADB_VER}"
  [[ "$(printf '%s\n' "11.5.2" "${MARIADB_VER}" | sort -V | head -1)" == "11.5.2" ]] || \
    die "MariaDB ${MARIADB_VER} is unsupported; install MariaDB 11.5.2 or newer"
fi

log "Creating system user and directory layout..."
if ! id somimas &>/dev/null; then
  useradd --system --home-dir "${DEPLOY_ROOT}" --shell /usr/sbin/nologin somimas
fi

install -d -o somimas -g somimas -m 0750 \
  "${DEPLOY_ROOT}" \
  "${DEPLOY_ROOT}/releases" \
  "${DEPLOY_ROOT}/scripts" \
  /var/lib/somimas/fineract-content \
  /var/lib/somimas/receipts \
  /var/log/somimas \
  /etc/somimas \
  /etc/somimas/nginx \
  /etc/somimas/mariadb \
  /var/backups/somimas
install -d -o www-data -g www-data -m 0755 /var/www/somimas /var/www/somimas/releases /var/www/certbot

# Install deployment scripts (symlink kit into /opt/somimas/scripts)
if [[ -d "${SCRIPT_DIR}" ]]; then
  for script in deploy.sh deploy-from-git.sh configure-nginx.sh rollback.sh backup.sh restore.sh; do
    if [[ -f "${SCRIPT_DIR}/${script}" ]]; then
      install -m 0755 "${SCRIPT_DIR}/${script}" "${DEPLOY_ROOT}/scripts/${script}"
    fi
  done
fi

log "Installing configuration files to /etc..."
install -m 0644 "${SCRIPT_DIR}/etc/mariadb/99-somimas.cnf" /etc/mysql/mariadb.conf.d/99-somimas.cnf
install -m 0640 "${SCRIPT_DIR}/etc/mariadb/bootstrap.sql" /etc/somimas/mariadb/bootstrap.sql
install -m 0644 "${SCRIPT_DIR}/etc/logrotate.d/somimas" /etc/logrotate.d/somimas
install -m 0644 "${SCRIPT_DIR}/etc/cron.d/somimas-backup" /etc/cron.d/somimas-backup
install -m 0644 "${SCRIPT_DIR}/etc/systemd/fineract.service" /etc/systemd/system/fineract.service
install -m 0644 "${SCRIPT_DIR}/etc/systemd/somimas-control-plane.service" /etc/systemd/system/somimas-control-plane.service
install -m 0644 "${SCRIPT_DIR}/etc/nginx/sites-available/somimas.conf" /etc/somimas/nginx/somimas.conf.template

# Env examples — do not overwrite production secrets
if [[ ! -f /etc/somimas/fineract.env ]]; then
  install -m 0640 "${SCRIPT_DIR}/etc/somimas/fineract.env.example" /etc/somimas/fineract.env
  chown root:somimas /etc/somimas/fineract.env
fi
if [[ ! -f /etc/somimas/control-plane.env ]]; then
  install -m 0640 "${SCRIPT_DIR}/etc/somimas/control-plane.env.example" /etc/somimas/control-plane.env
  chown root:somimas /etc/somimas/control-plane.env
fi

# Nginx site. Before the first certificate exists, install an HTTP-only
# bootstrap site so nginx -t succeeds and the ACME challenge is reachable.
NGINX_SITE="/etc/nginx/sites-available/somimas.conf"
if [[ -f "/etc/letsencrypt/live/${APP_DOMAIN}/fullchain.pem" ]]; then
  sed "s/APP_DOMAIN/${APP_DOMAIN//\//\\/}/g" \
    "${SCRIPT_DIR}/etc/nginx/sites-available/somimas.conf" > "${NGINX_SITE}"
elif [[ ! -f "${NGINX_SITE}" ]] || grep -q 'APP_DOMAIN' "${NGINX_SITE}" 2>/dev/null; then
  cat >"${NGINX_SITE}" <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name ${APP_DOMAIN};
    root /var/www/somimas/current;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        try_files \$uri \$uri/ /index.html =404;
    }
}
EOF
fi
ln -sf /etc/nginx/sites-available/somimas.conf /etc/nginx/sites-enabled/somimas.conf
rm -f /etc/nginx/sites-enabled/default

log "Configuring UFW..."
ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw allow 'Nginx Full'
ufw --force enable

log "Starting MariaDB..."
systemctl enable mariadb
systemctl restart mariadb

# Bootstrap databases if not already present
if ! mariadb -e "SELECT 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='somimas_control'" | grep -q 1; then
  log "Running MariaDB bootstrap SQL (edit passwords in /etc/somimas/mariadb/bootstrap.sql first)..."
  if grep -q 'CHANGE_ME' /etc/somimas/mariadb/bootstrap.sql; then
    log "WARNING: bootstrap.sql still contains CHANGE_ME placeholders."
    log "         Edit /etc/somimas/mariadb/bootstrap.sql and /etc/somimas/*.env, then run:"
    log "         sudo mariadb < /etc/somimas/mariadb/bootstrap.sql"
  else
    mariadb < /etc/somimas/mariadb/bootstrap.sql
  fi
else
  log "MariaDB bootstrap already applied (somimas_control exists)."
fi

log "Enabling systemd units..."
systemctl daemon-reload
systemctl enable fineract.service somimas-control-plane.service

log "Testing nginx configuration..."
nginx -t

systemctl enable nginx
systemctl reload nginx

log "Bootstrap complete."
log ""
log "Next steps:"
log "  1. Edit /etc/somimas/mariadb/bootstrap.sql passwords, run: sudo mariadb < /etc/somimas/mariadb/bootstrap.sql"
log "  2. Edit /etc/somimas/fineract.env and /etc/somimas/control-plane.env"
log "  3. Issue TLS and enable the production Nginx site:"
log "       sudo APP_DOMAIN=${APP_DOMAIN} ADMIN_EMAIL=admin@softecki.com ${DEPLOY_ROOT}/scripts/configure-nginx.sh"
log "  4. Pull, build and deploy from Git:"
log "       ${DEPLOY_ROOT}/scripts/deploy-from-git.sh --repo-dir /path/to/repo --branch main"
log "  5. Run verification checklist: deployment/native-vps/docs/VERIFICATION_CHECKLIST.md"
