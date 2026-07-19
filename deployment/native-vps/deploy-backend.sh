#!/usr/bin/env bash
# deploy-backend.sh — FAST PATH for backend-only (Java) changes.
#
# Pulls Git, rebuilds ONLY the Fineract provider and SaaS control-plane jars
# (with the Gradle build cache so unchanged modules are not recompiled), and
# restarts the Java services. The Angular frontend is left untouched.
#
# For UI/branding changes use deploy-frontend.sh; to redeploy everything use
# deploy-from-git.sh.
set -euo pipefail

REPO_DIR="${REPO_DIR:-$(pwd)}"
BRANCH="${BRANCH:-main}"
APP_DOMAIN="${APP_DOMAIN:-microfinance.softecki.com}"
GRADLE_VERSION="8.14.3"
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
BACKEND_DIR="${REPO_DIR}/Somimas-main"
KIT_DIR="${REPO_DIR}/deployment/native-vps"

[[ -d "${REPO_DIR}/.git" ]] || { echo "${REPO_DIR} is not a Git repository root" >&2; exit 1; }
[[ -d "${BACKEND_DIR}" && -d "${KIT_DIR}" ]] || {
  echo "Expected Somimas-main and deployment/native-vps under ${REPO_DIR}" >&2
  exit 1
}

if [[ "${NO_PULL}" != "true" ]]; then
  echo "[git] Pulling origin/${BRANCH}..."
  git -C "${REPO_DIR}" fetch origin "${BRANCH}"
  git -C "${REPO_DIR}" checkout "${BRANCH}"
  git -C "${REPO_DIR}" pull --ff-only origin "${BRANCH}"
fi

if [[ -f "${BACKEND_DIR}/gradle/wrapper/gradle-wrapper.jar" ]]; then
  GRADLE=("bash" "${BACKEND_DIR}/gradlew")
else
  GRADLE_HOME="${HOME}/.local/opt/gradle-${GRADLE_VERSION}"
  if [[ ! -x "${GRADLE_HOME}/bin/gradle" ]]; then
    echo "[build] Installing Gradle ${GRADLE_VERSION}..."
    TMP_ZIP="$(mktemp --suffix=.zip)"
    curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "${TMP_ZIP}"
    mkdir -p "${HOME}/.local/opt"
    unzip -q "${TMP_ZIP}" -d "${HOME}/.local/opt"
    rm -f "${TMP_ZIP}"
  fi
  GRADLE=("${GRADLE_HOME}/bin/gradle")
fi

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

GRADLE_XMX="${GRADLE_XMX:-2g}"
GRADLE_JVMARGS="-Xmx${GRADLE_XMX} -XX:MaxMetaspaceSize=512m --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED --add-exports=java.naming/com.sun.jndi.ldap=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.security=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.management/javax.management=ALL-UNNAMED --add-opens=java.naming/javax.naming=ALL-UNNAMED --add-opens=java.rmi/sun.rmi.transport=null"

echo "[build] Building backend jars (heap ${GRADLE_XMX}, build cache on)..."
(
  cd "${BACKEND_DIR}"
  # --build-cache reuses outputs of unchanged modules, so an incremental
  # change only recompiles what it touched instead of the whole tree.
  "${GRADLE[@]}" --no-daemon --max-workers=2 --build-cache \
    -Dorg.gradle.jvmargs="${GRADLE_JVMARGS}" \
    -Dorg.gradle.parallel=false \
    :fineract-provider:bootJar :saas-control-plane:bootJar
)

find_jar() {
  local directory="$1" jar
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

# Refresh the installed deploy.sh so the --backend-only flag is available.
sudo install -m 0755 "${KIT_DIR}/deploy.sh" /opt/somimas/scripts/deploy.sh

sudo /opt/somimas/scripts/deploy.sh \
  --backend-only \
  --fineract-jar "${FINERACT_JAR}" \
  --control-plane-jar "${CONTROL_JAR}" \
  --app-domain "${APP_DOMAIN}"

echo "Backend deploy complete: https://${APP_DOMAIN}"
