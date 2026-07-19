#!/usr/bin/env bash
# deploy.sh — Deploy a new Somimas release (backend jars + Angular frontend).
#
# Usage:
#   sudo deploy.sh \
#     --fineract-jar /path/to/fineract-provider.jar \
#     --control-plane-jar /path/to/saas-control-plane.jar \
#     --frontend-dir /path/to/dist/web-app \
#     [--release-id 20260718T141500Z] \
#     [--app-domain app.example.com] \
#     [--frontend-only] [--backend-only] \
#     [--skip-health-check]
#
# Modes:
#   (default)         Deploy backend jars AND frontend; restart Java + reload Nginx.
#   --frontend-only   Deploy only the Angular frontend; reload Nginx, DO NOT touch Java.
#   --backend-only    Deploy only the backend jars; restart Java, reuse current frontend.
#
set -euo pipefail

DEPLOY_ROOT="/opt/somimas"
WWW_ROOT="/var/www/somimas"
RELEASE_ID=""
FINERACT_JAR=""
CONTROL_PLANE_JAR=""
FRONTEND_DIR=""
APP_DOMAIN="${APP_DOMAIN:-microfinance.softecki.com}"
SKIP_HEALTH=false
MODE="full"   # full | frontend | backend

log() { printf '[deploy] %s\n' "$*"; }
die() { printf '[deploy] ERROR: %s\n' "$*" >&2; exit 1; }

usage() {
  sed -n '2,17p' "$0"
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --fineract-jar)        FINERACT_JAR="$2"; shift 2 ;;
    --control-plane-jar)   CONTROL_PLANE_JAR="$2"; shift 2 ;;
    --frontend-dir)        FRONTEND_DIR="$2"; shift 2 ;;
    --release-id)          RELEASE_ID="$2"; shift 2 ;;
    --app-domain)          APP_DOMAIN="$2"; shift 2 ;;
    --frontend-only)       MODE="frontend"; shift ;;
    --backend-only)        MODE="backend"; shift ;;
    --skip-health-check)   SKIP_HEALTH=true; shift ;;
    -h|--help)             usage ;;
    *) die "Unknown argument: $1" ;;
  esac
done

[[ "${EUID:-$(id -u)}" -ne 0 ]] && die "Run as root"

DEPLOY_BACKEND=true
DEPLOY_FRONTEND=true
[[ "${MODE}" == "frontend" ]] && DEPLOY_BACKEND=false
[[ "${MODE}" == "backend" ]] && DEPLOY_FRONTEND=false

if [[ "${DEPLOY_BACKEND}" == "true" ]]; then
  [[ -n "${FINERACT_JAR}" && -f "${FINERACT_JAR}" ]] || die "--fineract-jar required and must exist"
  [[ -n "${CONTROL_PLANE_JAR}" && -f "${CONTROL_PLANE_JAR}" ]] || die "--control-plane-jar required and must exist"
fi

if [[ "${DEPLOY_FRONTEND}" == "true" ]]; then
  [[ -n "${FRONTEND_DIR}" && -d "${FRONTEND_DIR}" ]] || die "--frontend-dir required and must exist"
  # Angular's application builder writes browser assets under a browser/
  # subdirectory. Accept either that directory or its parent.
  if [[ ! -f "${FRONTEND_DIR}/index.html" && -f "${FRONTEND_DIR}/browser/index.html" ]]; then
    FRONTEND_DIR="${FRONTEND_DIR}/browser"
  fi
  [[ -f "${FRONTEND_DIR}/index.html" ]] || die "Frontend directory must contain index.html (or browser/index.html)"
fi

if [[ -z "${RELEASE_ID}" ]]; then
  RELEASE_ID="$(date -u +%Y%m%dT%H%M%SZ)"
fi

BACKEND_RELEASE="${DEPLOY_ROOT}/releases/${RELEASE_ID}"
FRONTEND_RELEASE="${WWW_ROOT}/releases/${RELEASE_ID}"
PREVIOUS_BACKEND="$(readlink -f "${DEPLOY_ROOT}/current" 2>/dev/null || true)"
PREVIOUS_FRONTEND="$(readlink -f "${WWW_ROOT}/current" 2>/dev/null || true)"

log "Release ID: ${RELEASE_ID} (mode: ${MODE})"

if [[ "${DEPLOY_BACKEND}" == "true" ]]; then
  install -d -o somimas -g somimas -m 0750 "${BACKEND_RELEASE}"
  log "Copying backend artifacts..."
  install -o somimas -g somimas -m 0640 "${FINERACT_JAR}" "${BACKEND_RELEASE}/fineract-provider.jar"
  install -o somimas -g somimas -m 0640 "${CONTROL_PLANE_JAR}" "${BACKEND_RELEASE}/saas-control-plane.jar"
fi

if [[ "${DEPLOY_FRONTEND}" != "true" ]]; then
  log "Skipping frontend (backend-only); current frontend release is left in place."
else
install -d -o www-data -g www-data -m 0755 "${FRONTEND_RELEASE}"

log "Copying frontend to ${FRONTEND_RELEASE}..."
rsync -a --delete "${FRONTEND_DIR}/" "${FRONTEND_RELEASE}/"

log "Generating env.js from env.template.js..."
ENV_TEMPLATE="${FRONTEND_RELEASE}/assets/env.template.js"
ENV_OUTPUT="${FRONTEND_RELEASE}/assets/env.js"
[[ -f "${ENV_TEMPLATE}" ]] || die "Missing ${ENV_TEMPLATE}"

export FINERACT_API_URLS="https://${APP_DOMAIN}"
export FINERACT_API_URL="https://${APP_DOMAIN}"
export FINERACT_API_PROVIDER="/fineract-provider/api"
export FINERACT_API_VERSION="/v1"
export FINERACT_API_ACTUATOR="/fineract-provider/actuator"
export FINERACT_PLATFORM_TENANT_IDENTIFIER="default"
export FINERACT_PLATFORM_TENANTS_IDENTIFIER="default"
export TENANT_LOGO_URL=""
export MIFOS_DEFAULT_LANGUAGE="en-US"
export MIFOS_SUPPORTED_LANGUAGES="en-US"
export MIFOS_PRELOAD_CLIENTS="true"
export MIFOS_DEFAULT_CHAR_DELIMITER=","
export MIFOS_ALLOW_SERVER_SWITCH_SELECTOR="false"
export MIFOS_DISPLAY_BACKEND_INFO="false"
export MIFOS_DISPLAY_TENANT_SELECTOR="false"
export MIFOS_WAIT_TIME_FOR_NOTIFICATIONS="60"
export MIFOS_WAIT_TIME_FOR_CATCHUP="30"
export MIFOS_SESSION_IDLE_TIMEOUT="300000"
export MIFOS_OAUTH_SERVER_ENABLED="false"
export MIFOS_OAUTH_SERVER_URL=""
export MIFOS_OAUTH_CLIENT_ID=""
export MIFOS_MIN_PASSWORD_LENGTH="12"
export MIFOS_HTTP_CACHE_ENABLED="true"
export VNEXT_API_URL="https://${APP_DOMAIN}/fineract-provider/api/v2"
export VNEXT_API_PROVIDER="/fineract-provider/api"
export VNEXT_API_VERSION="/v2"
export VNEXT_INTERBANK_TRANSFERS="false"
export FINERACT_PLUGIN_OIDC_ENABLED="false"
export FINERACT_PLUGIN_OIDC_BASE_URL=""
export FINERACT_PLUGIN_OIDC_CLIENT_ID=""
export FINERACT_PLUGIN_OIDC_API_URL=""
export FINERACT_PLUGIN_OIDC_FRONTEND_URL=""
export CONTROL_PLANE_API_URL="https://${APP_DOMAIN}/saas-api"
export SOMIMAS_SAAS_MODE="true"

envsubst < "${ENV_TEMPLATE}" > "${ENV_OUTPUT}.tmp"
mv "${ENV_OUTPUT}.tmp" "${ENV_OUTPUT}"
chown www-data:www-data "${ENV_OUTPUT}"
chmod 0644 "${ENV_OUTPUT}"
fi

log "Switching current symlinks..."
if [[ "${DEPLOY_BACKEND}" == "true" ]]; then
  ln -sfn "${BACKEND_RELEASE}" "${DEPLOY_ROOT}/current"
  [[ -n "${PREVIOUS_BACKEND}" ]] && basename "${PREVIOUS_BACKEND}" > "${DEPLOY_ROOT}/.previous-release" 2>/dev/null || true
  echo "${RELEASE_ID}" > "${DEPLOY_ROOT}/.current-release"
fi
if [[ "${DEPLOY_FRONTEND}" == "true" ]]; then
  ln -sfn "${FRONTEND_RELEASE}" "${WWW_ROOT}/current"
  [[ -n "${PREVIOUS_FRONTEND}" ]] && basename "${PREVIOUS_FRONTEND}" > "${WWW_ROOT}/.previous-release" 2>/dev/null || true
  echo "${RELEASE_ID}" > "${WWW_ROOT}/.current-release"
fi

systemctl daemon-reload
if [[ "${DEPLOY_BACKEND}" == "true" ]]; then
  log "Restarting Java services..."
  systemctl restart fineract.service
  systemctl restart somimas-control-plane.service
else
  log "Frontend-only deploy: leaving Java services running."
fi
log "Reloading Nginx..."
systemctl reload nginx

# Frontend-only deploys do not touch Java, so skip the Java health checks.
if [[ "${DEPLOY_BACKEND}" != "true" ]]; then
  SKIP_HEALTH=true
fi

if [[ "${SKIP_HEALTH}" == "false" ]]; then
  log "Running health checks..."
  sleep 5

  fineract_ok=false
  control_ok=false

  for i in $(seq 1 30); do
    if curl -sf "http://127.0.0.1:8080/fineract-provider/actuator/health" >/dev/null 2>&1; then
      fineract_ok=true
    fi
    if curl -sf "http://127.0.0.1:8090/saas-api/actuator/health" >/dev/null 2>&1; then
      control_ok=true
    fi
    if [[ "${fineract_ok}" == "true" && "${control_ok}" == "true" ]]; then
      break
    fi
    sleep 5
  done

  if [[ "${fineract_ok}" != "true" || "${control_ok}" != "true" ]]; then
    log "WARNING: One or more services failed health checks (deploy NOT rolled back)."
    [[ "${fineract_ok}" != "true" ]] && log "  - fineract.service unhealthy on http://127.0.0.1:8080/fineract-provider/actuator/health"
    [[ "${control_ok}" != "true" ]] && log "  - somimas-control-plane.service unhealthy on http://127.0.0.1:8090/saas-api/actuator/health"
    log "Recent service logs:"
    if [[ "${fineract_ok}" != "true" ]]; then
      log "----- fineract.service (systemd) -----"
      journalctl -u fineract.service -n 20 --no-pager 2>&1 | sed 's/^/[deploy]   /' || true
      if [[ -f /var/log/somimas/fineract.log ]]; then
        log "----- /var/log/somimas/fineract.log (last 80 lines) -----"
        tail -n 80 /var/log/somimas/fineract.log 2>&1 | sed 's/^/[deploy]   /' || true
      else
        log "  (missing /var/log/somimas/fineract.log — check permissions /var/log/somimas)"
      fi
    fi
    if [[ "${control_ok}" != "true" ]]; then
      log "----- somimas-control-plane.service (systemd) -----"
      journalctl -u somimas-control-plane.service -n 20 --no-pager 2>&1 | sed 's/^/[deploy]   /' || true
      if [[ -f /var/log/somimas/control-plane.log ]]; then
        log "----- /var/log/somimas/control-plane.log (last 80 lines) -----"
        tail -n 80 /var/log/somimas/control-plane.log 2>&1 | sed 's/^/[deploy]   /' || true
      fi
    fi
    log "To roll back manually: sudo ${DEPLOY_ROOT}/scripts/rollback.sh"
  else
    log "Health checks passed."
  fi
fi

log "Deploy complete: ${RELEASE_ID}"
log "  Backend:  ${BACKEND_RELEASE}"
log "  Frontend: ${FRONTEND_RELEASE}"
