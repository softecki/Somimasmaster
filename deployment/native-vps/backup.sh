#!/usr/bin/env bash
# backup.sh — Logical backup of all Somimas databases and content directories.
#
# Usage:
#   sudo backup.sh
#   sudo backup.sh --dest /var/backups/somimas/manual-20260718
#
# Reads DB credentials from /etc/somimas/backup.env (optional) or defaults.
#
set -euo pipefail

BACKUP_ROOT="${BACKUP_DEST:-/var/backups/somimas}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DEST="${BACKUP_ROOT}/${TIMESTAMP}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"

ENV_FILE="/etc/somimas/backup.env"
DB_HOST="127.0.0.1"
DB_PORT="3306"
DB_USER="somimas_backup"
DB_PASSWORD=""

log() { printf '[backup] %s\n' "$*"; }
die() { printf '[backup] ERROR: %s\n' "$*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dest) BACKUP_ROOT="$(dirname "$2")"; TIMESTAMP="$(basename "$2")"; DEST="$2"; shift 2 ;;
    --retention-days) RETENTION_DAYS="$2"; shift 2 ;;
    -h|--help) sed -n '2,8p' "$0"; exit 0 ;;
    *) die "Unknown argument: $1" ;;
  esac
done

[[ "${EUID:-$(id -u)}" -ne 0 ]] && die "Run as root"

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

[[ -n "${DB_PASSWORD}" ]] || {
  # Try to read from bootstrap if backup.env not configured
  if [[ -f /etc/somimas/mariadb/bootstrap.sql ]]; then
    DB_PASSWORD="$(grep -m1 "somimas_backup'@'localhost'" /etc/somimas/mariadb/bootstrap.sql \
      | sed -n "s/.*IDENTIFIED BY '\([^']*\)'.*/\1/p" || true)"
  fi
}

[[ -n "${DB_PASSWORD}" && "${DB_PASSWORD}" != CHANGE_ME* ]] \
  || die "Configure ${ENV_FILE} with DB_PASSWORD or update bootstrap.sql passwords"

MYSQLDUMP_OPTS=(
  --host="${DB_HOST}"
  --port="${DB_PORT}"
  --user="${DB_USER}"
  --password="${DB_PASSWORD}"
  --single-transaction
  --quick
  --routines
  --triggers
  --events
  --hex-blob
  --default-character-set=utf8mb4
)

install -d -m 0700 "${DEST}"
install -d -m 0700 "${DEST}/databases"

log "Discovering databases..."
STATIC_DBS=(somimas_control fineract_tenants fineract_default)
DYNAMIC_DBS=()
while IFS= read -r db; do
  DYNAMIC_DBS+=("${db}")
done < <(mariadb "${MYSQLDUMP_OPTS[@]}" --batch --skip-column-names \
  -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME LIKE 'somimas\_%' ESCAPE '\\\\' ORDER BY SCHEMA_NAME")

ALL_DBS=("${STATIC_DBS[@]}" "${DYNAMIC_DBS[@]}")
log "Backing up ${#ALL_DBS[@]} database(s)..."

for db in "${ALL_DBS[@]}"; do
  if mariadb "${MYSQLDUMP_OPTS[@]}" -e "USE \`${db}\`" 2>/dev/null; then
    log "  dumping ${db}..."
    mariadb-dump "${MYSQLDUMP_OPTS[@]}" "${db}" \
      | gzip -9 > "${DEST}/databases/${db}.sql.gz"
  else
    log "  skipping ${db} (not present)"
  fi
done

log "Archiving content directories..."
tar -czf "${DEST}/fineract-content.tar.gz" -C /var/lib/somimas fineract-content 2>/dev/null || true
tar -czf "${DEST}/receipts.tar.gz" -C /var/lib/somimas receipts 2>/dev/null || true

# Manifest
{
  echo "timestamp=${TIMESTAMP}"
  echo "hostname=$(hostname -f)"
  echo "databases=${ALL_DBS[*]}"
  echo "kit=native-vps"
} > "${DEST}/manifest.txt"

sha256sum "${DEST}"/databases/*.sql.gz "${DEST}"/*.tar.gz 2>/dev/null > "${DEST}/SHA256SUMS" || true

log "Pruning backups older than ${RETENTION_DAYS} days..."
find "${BACKUP_ROOT}" -mindepth 1 -maxdepth 1 -type d -mtime "+${RETENTION_DAYS}" -exec rm -rf {} + 2>/dev/null || true

log "Backup complete: ${DEST}"
