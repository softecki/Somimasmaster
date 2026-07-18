#!/usr/bin/env bash
# restore.sh — Restore Somimas from a backup created by backup.sh.
#
# Usage:
#   sudo restore.sh --from /var/backups/somimas/20260718T021500Z
#   sudo restore.sh --from /var/backups/somimas/20260718T021500Z --skip-content
#
# WARNING: This stops services and overwrites databases. Use on staging first.
#
set -euo pipefail

SOURCE=""
SKIP_CONTENT=false
DB_HOST="127.0.0.1"
DB_PORT="3306"
DB_USER="root"
DB_PASSWORD=""

ENV_FILE="/etc/somimas/backup.env"

log() { printf '[restore] %s\n' "$*"; }
die() { printf '[restore] ERROR: %s\n' "$*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --from)         SOURCE="$2"; shift 2 ;;
    --skip-content) SKIP_CONTENT=true; shift ;;
    -h|--help)      sed -n '2,9p' "$0"; exit 0 ;;
    *) die "Unknown argument: $1" ;;
  esac
done

[[ "${EUID:-$(id -u)}" -ne 0 ]] && die "Run as root"
[[ -n "${SOURCE}" && -d "${SOURCE}" ]] || die "--from required and must be an existing backup directory"
[[ -d "${SOURCE}/databases" ]] || die "Missing ${SOURCE}/databases"

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

if [[ -f "${SOURCE}/SHA256SUMS" ]]; then
  log "Verifying checksums..."
  (cd "${SOURCE}" && sha256sum -c SHA256SUMS) || die "Checksum verification failed"
fi

log "Stopping application services..."
systemctl stop somimas-control-plane.service fineract.service || true

MYSQL_OPTS=(
  --host="${DB_HOST}"
  --port="${DB_PORT}"
  --user="${DB_USER}"
)
[[ -n "${DB_PASSWORD}" ]] && MYSQL_OPTS+=(--password="${DB_PASSWORD}")

restore_db() {
  local archive="$1"
  local db
  db="$(basename "${archive}" .sql.gz)"
  log "Restoring database ${db}..."
  mariadb "${MYSQL_OPTS[@]}" -e "CREATE DATABASE IF NOT EXISTS \`${db}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
  gunzip -c "${archive}" | mariadb "${MYSQL_OPTS[@]}" "${db}"
}

shopt -s nullglob
for archive in "${SOURCE}/databases/"*.sql.gz; do
  restore_db "${archive}"
done
shopt -u nullglob

if [[ "${SKIP_CONTENT}" == "false" ]]; then
  if [[ -f "${SOURCE}/fineract-content.tar.gz" ]]; then
    log "Restoring fineract-content..."
    rm -rf /var/lib/somimas/fineract-content
    tar -xzf "${SOURCE}/fineract-content.tar.gz" -C /var/lib/somimas
    chown -R somimas:somimas /var/lib/somimas/fineract-content
  fi
  if [[ -f "${SOURCE}/receipts.tar.gz" ]]; then
    log "Restoring receipts..."
    rm -rf /var/lib/somimas/receipts
    tar -xzf "${SOURCE}/receipts.tar.gz" -C /var/lib/somimas
    chown -R somimas:somimas /var/lib/somimas/receipts
  fi
fi

log "Starting services..."
systemctl start fineract.service somimas-control-plane.service

sleep 5
curl -sf "http://127.0.0.1:8080/fineract-provider/actuator/health" >/dev/null \
  || log "WARNING: Fineract health check failed — inspect logs"
curl -sf "http://127.0.0.1:8090/saas-api/actuator/health" >/dev/null \
  || log "WARNING: Control plane health check failed — inspect logs"

log "Restore from ${SOURCE} complete."
