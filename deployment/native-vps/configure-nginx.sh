#!/usr/bin/env bash
# Installs the HTTP bootstrap site, obtains TLS, then enables the full
# same-origin Angular + Fineract + control-plane Nginx configuration.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DOMAIN="${APP_DOMAIN:-microfinance.softecki.com}"
ADMIN_EMAIL="${ADMIN_EMAIL:-filbertnyakunga@softecki.co.tz}"
SITE="/etc/nginx/sites-available/somimas.conf"
TEMPLATE="${NGINX_TEMPLATE:-${SCRIPT_DIR}/etc/nginx/sites-available/somimas.conf}"
if [[ ! -f "${TEMPLATE}" ]]; then
  TEMPLATE="/etc/somimas/nginx/somimas.conf.template"
fi

[[ "${EUID:-$(id -u)}" -eq 0 ]] || {
  echo "Run as root: sudo APP_DOMAIN=${APP_DOMAIN} ADMIN_EMAIL=${ADMIN_EMAIL} $0" >&2
  exit 1
}
[[ -f "${TEMPLATE}" ]] || {
  echo "Nginx template not found: ${TEMPLATE}" >&2
  exit 1
}

install -d -m 0755 /var/www/certbot /var/www/somimas

if [[ ! -f "/etc/letsencrypt/live/${APP_DOMAIN}/fullchain.pem" ]]; then
  cat >"${SITE}" <<EOF
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
  ln -sfn "${SITE}" /etc/nginx/sites-enabled/somimas.conf
  rm -f /etc/nginx/sites-enabled/default
  nginx -t
  systemctl reload nginx

  certbot certonly --webroot \
    --webroot-path /var/www/certbot \
    --domain "${APP_DOMAIN}" \
    --email "${ADMIN_EMAIL}" \
    --agree-tos \
    --non-interactive
fi

sed "s/APP_DOMAIN/${APP_DOMAIN//\//\\/}/g" \
  "${TEMPLATE}" >"${SITE}"
ln -sfn "${SITE}" /etc/nginx/sites-enabled/somimas.conf
nginx -t
systemctl reload nginx
systemctl enable --now certbot.timer

echo "Nginx enabled: https://${APP_DOMAIN}"
