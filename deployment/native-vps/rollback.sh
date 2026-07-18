#!/usr/bin/env bash
# rollback.sh — Revert to the previous release (or a specific release ID).
#
# Usage:
#   sudo rollback.sh                  # roll back to .previous-release
#   sudo rollback.sh --release-id 20260718T120000Z
#   sudo rollback.sh --list
#
set -euo pipefail

DEPLOY_ROOT="/opt/somimas"
WWW_ROOT="/var/www/somimas"
RELEASE_ID=""
LIST=false

log() { printf '[rollback] %s\n' "$*"; }
die() { printf '[rollback] ERROR: %s\n' "$*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --release-id) RELEASE_ID="$2"; shift 2 ;;
    --list)       LIST=true; shift ;;
    -h|--help)
      sed -n '2,8p' "$0"; exit 0 ;;
    *) die "Unknown argument: $1" ;;
  esac
done

[[ "${EUID:-$(id -u)}" -ne 0 ]] && die "Run as root"

if [[ "${LIST}" == "true" ]]; then
  log "Backend releases:"
  ls -1 "${DEPLOY_ROOT}/releases" 2>/dev/null || true
  log "Frontend releases:"
  ls -1 "${WWW_ROOT}/releases" 2>/dev/null || true
  exit 0
fi

if [[ -z "${RELEASE_ID}" ]]; then
  if [[ -f "${DEPLOY_ROOT}/.previous-release" ]]; then
    RELEASE_ID="$(cat "${DEPLOY_ROOT}/.previous-release")"
  else
    die "No --release-id given and ${DEPLOY_ROOT}/.previous-release not found"
  fi
fi

BACKEND_TARGET="${DEPLOY_ROOT}/releases/${RELEASE_ID}"
FRONTEND_TARGET="${WWW_ROOT}/releases/${RELEASE_ID}"

[[ -d "${BACKEND_TARGET}" ]] || die "Backend release not found: ${BACKEND_TARGET}"
[[ -d "${FRONTEND_TARGET}" ]] || die "Frontend release not found: ${FRONTEND_TARGET}"

CURRENT_BACKEND="$(readlink -f "${DEPLOY_ROOT}/current" 2>/dev/null || true)"
if [[ -n "${CURRENT_BACKEND}" ]]; then
  basename "${CURRENT_BACKEND}" > "${DEPLOY_ROOT}/.previous-release"
fi
CURRENT_FRONTEND="$(readlink -f "${WWW_ROOT}/current" 2>/dev/null || true)"
if [[ -n "${CURRENT_FRONTEND}" ]]; then
  basename "${CURRENT_FRONTEND}" > "${WWW_ROOT}/.previous-release"
fi

log "Rolling back to ${RELEASE_ID}..."
ln -sfn "${BACKEND_TARGET}" "${DEPLOY_ROOT}/current"
ln -sfn "${FRONTEND_TARGET}" "${WWW_ROOT}/current"
echo "${RELEASE_ID}" > "${DEPLOY_ROOT}/.current-release"
echo "${RELEASE_ID}" > "${WWW_ROOT}/.current-release"

systemctl restart fineract.service somimas-control-plane.service
systemctl reload nginx

sleep 5
curl -sf "http://127.0.0.1:8080/fineract-provider/actuator/health" >/dev/null \
  || die "Fineract health check failed after rollback"
curl -sf "http://127.0.0.1:8090/saas-api/actuator/health" >/dev/null \
  || die "Control plane health check failed after rollback"

log "Rollback to ${RELEASE_ID} complete."
