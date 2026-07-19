#!/usr/bin/env bash
# Pulls the Git repository, builds both Java services and the Angular frontend,
# then installs one atomic release through deploy.sh.
set -euo pipefail

REPO_DIR="${REPO_DIR:-$(pwd)}"
BRANCH="${BRANCH:-main}"
APP_DOMAIN="${APP_DOMAIN:-microfinance.softecki.com}"
GRADLE_VERSION="8.14.3"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo-dir) REPO_DIR="$2"; shift 2 ;;
    --branch) BRANCH="$2"; shift 2 ;;
    --app-domain) APP_DOMAIN="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--repo-dir /path/to/repo] [--branch main] [--app-domain microfinance.softecki.com]"
      exit 0
      ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

REPO_DIR="$(cd "${REPO_DIR}" && pwd)"
BACKEND_DIR="${REPO_DIR}/Somimas-main"
FRONTEND_DIR="${REPO_DIR}/Somimas_frontend-main"
KIT_DIR="${REPO_DIR}/deployment/native-vps"

[[ -d "${REPO_DIR}/.git" ]] || {
  echo "${REPO_DIR} is not a Git repository root" >&2
  exit 1
}
[[ -d "${BACKEND_DIR}" && -d "${FRONTEND_DIR}" && -d "${KIT_DIR}" ]] || {
  echo "Expected Somimas-main, Somimas_frontend-main and deployment/native-vps under ${REPO_DIR}" >&2
  exit 1
}

echo "[git] Pulling origin/${BRANCH}..."
git -C "${REPO_DIR}" fetch origin "${BRANCH}"
git -C "${REPO_DIR}" checkout "${BRANCH}"
git -C "${REPO_DIR}" pull --ff-only origin "${BRANCH}"

if [[ -f "${BACKEND_DIR}/gradle/wrapper/gradle-wrapper.jar" ]]; then
  GRADLE=("bash" "${BACKEND_DIR}/gradlew")
else
  GRADLE_HOME="${HOME}/.local/opt/gradle-${GRADLE_VERSION}"
  if [[ ! -x "${GRADLE_HOME}/bin/gradle" ]]; then
    echo "[build] Installing Gradle ${GRADLE_VERSION} for the deploy user..."
    TMP_ZIP="$(mktemp --suffix=.zip)"
    curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "${TMP_ZIP}"
    mkdir -p "${HOME}/.local/opt"
    unzip -q "${TMP_ZIP}" -d "${HOME}/.local/opt"
    rm -f "${TMP_ZIP}"
  fi
  GRADLE=("${GRADLE_HOME}/bin/gradle")
fi

# JAVA_HOME must be the JDK root (…/java-21-openjdk-amd64), never …/bin/java
if [[ -z "${JAVA_HOME:-}" ]] || [[ ! -x "${JAVA_HOME}/bin/java" ]]; then
  if [[ -x /usr/lib/jvm/java-21-openjdk-amd64/bin/java ]]; then
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  elif command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(readlink -f "$(command -v java)")"
    export JAVA_HOME="$(dirname "$(dirname "${JAVA_BIN}")")"
  fi
fi
if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  export PATH="${JAVA_HOME}/bin:${PATH}"
  echo "[build] Using JAVA_HOME=${JAVA_HOME}"
else
  echo "JAVA_HOME is invalid. Set it to the JDK root, e.g.:" >&2
  echo "  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64" >&2
  exit 1
fi

# The repo's gradle.properties requests -Xmx12g, which OOM-kills the daemon on
# small VPSes. Override with a modest heap (tunable via GRADLE_XMX) while
# keeping the module flags the Fineract build needs to compile.
GRADLE_XMX="${GRADLE_XMX:-2g}"
GRADLE_JVMARGS="-Xmx${GRADLE_XMX} -XX:MaxMetaspaceSize=512m --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED --add-exports=java.naming/com.sun.jndi.ldap=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.security=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.management/javax.management=ALL-UNNAMED --add-opens=java.naming/javax.naming=ALL-UNNAMED --add-opens=java.rmi/sun.rmi.transport=null"

echo "[build] Building Fineract (including the Somimas SaaS starter) and control plane (heap ${GRADLE_XMX})..."
(
  cd "${BACKEND_DIR}"
  "${GRADLE[@]}" --no-daemon --max-workers=2 --build-cache \
    -Dorg.gradle.jvmargs="${GRADLE_JVMARGS}" \
    -Dorg.gradle.parallel=false \
    :fineract-provider:bootJar :saas-control-plane:bootJar
)

# Cap the Node heap so the Angular build is not OOM-killed on small VPSes
# (tunable via NODE_XMX_MB, in megabytes).
NODE_XMX_MB="${NODE_XMX_MB:-2048}"
export NODE_OPTIONS="--max-old-space-size=${NODE_XMX_MB}"

echo "[build] Building Angular production frontend (Node heap ${NODE_XMX_MB} MB)..."
(
  cd "${FRONTEND_DIR}"
  npm ci
  npm run build
)

find_jar() {
  local directory="$1"
  local jar
  for jar in "${directory}"/*.jar; do
    [[ -f "${jar}" ]] || continue
    [[ "${jar}" == *-plain.jar ]] && continue
    printf '%s\n' "${jar}"
    return 0
  done
  return 1
}

FINERACT_JAR="$(find_jar "${BACKEND_DIR}/fineract-provider/build/libs")"
CONTROL_JAR="$(find_jar "${BACKEND_DIR}/saas-control-plane/build/libs")"
ANGULAR_DIST="${FRONTEND_DIR}/dist/web-app"

echo "[install] Refreshing versioned service and deployment scripts..."
sudo install -m 0755 "${KIT_DIR}/deploy.sh" /opt/somimas/scripts/deploy.sh
sudo install -m 0755 "${KIT_DIR}/deploy-from-git.sh" /opt/somimas/scripts/deploy-from-git.sh
sudo install -m 0755 "${KIT_DIR}/deploy-frontend.sh" /opt/somimas/scripts/deploy-frontend.sh
sudo install -m 0755 "${KIT_DIR}/deploy-backend.sh" /opt/somimas/scripts/deploy-backend.sh
sudo install -m 0755 "${KIT_DIR}/configure-nginx.sh" /opt/somimas/scripts/configure-nginx.sh
sudo install -m 0755 "${KIT_DIR}/rollback.sh" /opt/somimas/scripts/rollback.sh
sudo install -m 0755 "${KIT_DIR}/backup.sh" /opt/somimas/scripts/backup.sh
sudo install -m 0755 "${KIT_DIR}/restore.sh" /opt/somimas/scripts/restore.sh
sudo install -m 0644 "${KIT_DIR}/etc/systemd/fineract.service" /etc/systemd/system/fineract.service
sudo install -m 0644 "${KIT_DIR}/etc/systemd/somimas-control-plane.service" \
  /etc/systemd/system/somimas-control-plane.service
sudo install -d -m 0750 /etc/somimas/nginx
sudo install -m 0644 "${KIT_DIR}/etc/nginx/sites-available/somimas.conf" \
  /etc/somimas/nginx/somimas.conf.template
sudo systemctl daemon-reload
sudo APP_DOMAIN="${APP_DOMAIN}" /opt/somimas/scripts/configure-nginx.sh

sudo /opt/somimas/scripts/deploy.sh \
  --fineract-jar "${FINERACT_JAR}" \
  --control-plane-jar "${CONTROL_JAR}" \
  --frontend-dir "${ANGULAR_DIST}" \
  --app-domain "${APP_DOMAIN}"

echo "Deployment complete: https://${APP_DOMAIN}"
