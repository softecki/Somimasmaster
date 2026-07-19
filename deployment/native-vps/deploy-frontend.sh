#!/usr/bin/env bash
# deploy-frontend.sh — FAST PATH for frontend-only changes.
#
# Pulls Git, rebuilds ONLY the Angular app, and installs it as a new frontend
# release. Java services (Fineract + control plane) are NOT rebuilt or
# restarted — only Nginx is reloaded. Use this for UI, branding, translation,
# theme and other web-app changes.
#
# For Java/backend changes use deploy-backend.sh; to redeploy everything use
# deploy-from-git.sh.
set -euo pipefail

REPO_DIR="${REPO_DIR:-$(pwd)}"
BRANCH="${BRANCH:-main}"
APP_DOMAIN="${APP_DOMAIN:-microfinance.softecki.com}"
NO_PULL=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo-dir) REPO_DIR="$2"; shift 2 ;;
    --branch) BRANCH="$2"; shift 2 ;;
    --app-domain) APP_DOMAIN="$2"; shift 2 ;;
    --no-pull) NO_PULL=true; shift ;;
    -h|--help)
      echo "Usage: $0 [--repo-dir /path/to/repo] [--branch main] [--app-domain host] [--no-pull]"
      exit 0
      ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

REPO_DIR="$(cd "${REPO_DIR}" && pwd)"
FRONTEND_DIR="${REPO_DIR}/Somimas_frontend-main"
KIT_DIR="${REPO_DIR}/deployment/native-vps"

[[ -d "${REPO_DIR}/.git" ]] || { echo "${REPO_DIR} is not a Git repository root" >&2; exit 1; }
[[ -d "${FRONTEND_DIR}" && -d "${KIT_DIR}" ]] || {
  echo "Expected Somimas_frontend-main and deployment/native-vps under ${REPO_DIR}" >&2
  exit 1
}

if [[ "${NO_PULL}" != "true" ]]; then
  echo "[git] Pulling origin/${BRANCH}..."
  git -C "${REPO_DIR}" fetch origin "${BRANCH}"
  git -C "${REPO_DIR}" checkout "${BRANCH}"
  git -C "${REPO_DIR}" pull --ff-only origin "${BRANCH}"
fi

echo "[build] Building Angular production frontend..."
(
  cd "${FRONTEND_DIR}"
  # npm ci wipes and reinstalls node_modules from scratch (slow). Only do the
  # full clean install when the lockfile actually changed; otherwise a plain
  # install is a near-instant no-op.
  if [[ ! -d node_modules ]] || [[ package-lock.json -nt node_modules/.package-lock.json ]]; then
    echo "[build] Dependencies changed — running npm ci..."
    npm ci
  else
    echo "[build] Dependencies unchanged — skipping install."
  fi
  npm run build
)

ANGULAR_DIST="${FRONTEND_DIR}/dist/web-app"

# Refresh the installed deploy.sh so the --frontend-only flag is available.
sudo install -m 0755 "${KIT_DIR}/deploy.sh" /opt/somimas/scripts/deploy.sh

sudo /opt/somimas/scripts/deploy.sh \
  --frontend-only \
  --frontend-dir "${ANGULAR_DIST}" \
  --app-domain "${APP_DOMAIN}"

echo "Frontend deploy complete: https://${APP_DOMAIN}"
echo "Tip: hard-refresh the browser (Ctrl+Shift+R) to bypass cached assets."
