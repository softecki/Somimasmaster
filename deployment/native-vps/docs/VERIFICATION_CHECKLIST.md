# Somimas Staging Verification Checklist

Run this checklist on a staging VPS (`APP_DOMAIN=staging.example.com`) after every deploy and before production cutover. Record pass/fail and the release ID (`/opt/somimas/.current-release`).

**Release under test:** ____________________  
**Tester / date:** ____________________

---

## 1. Infrastructure

| # | Check | Command / action | Pass |
|---|-------|------------------|------|
| 1.1 | MariaDB listening on localhost only | `ss -lntp \| grep 3306` → `127.0.0.1` | ☐ |
| 1.2 | Fineract bound to localhost | `ss -lntp \| grep 8080` → `127.0.0.1` | ☐ |
| 1.3 | Control plane bound to localhost | `ss -lntp \| grep 8090` → `127.0.0.1` | ☐ |
| 1.4 | UFW active, only 22/80/443 open | `sudo ufw status` | ☐ |
| 1.5 | TLS valid | `curl -sI https://APP_DOMAIN \| head -1` → `200` or `301` | ☐ |
| 1.6 | HTTP redirects to HTTPS | `curl -sI http://APP_DOMAIN \| grep -i location` | ☐ |

---

## 2. Service health

| # | Check | Command / action | Pass |
|---|-------|------------------|------|
| 2.1 | Fineract actuator UP | `curl -sf https://APP_DOMAIN/fineract-provider/actuator/health` | ☐ |
| 2.2 | Control plane actuator UP | `curl -sf https://APP_DOMAIN/saas-api/actuator/health` | ☐ |
| 2.3 | systemd units active | `systemctl is-active fineract somimas-control-plane nginx mariadb` | ☐ |
| 2.4 | No recent restarts (crash loop) | `systemctl status fineract \| grep Active` (< 5 min if just deployed) | ☐ |
| 2.5 | Log rotation configured | `ls /etc/logrotate.d/somimas` | ☐ |

---

## 3. Frontend

| # | Check | Command / action | Pass |
|---|-------|------------------|------|
| 3.1 | SPA loads | Open `https://APP_DOMAIN/` — login page renders | ☐ |
| 3.2 | `env.js` present, not cached stale | `curl -s https://APP_DOMAIN/assets/env.js \| grep fineractApiUrl` | ☐ |
| 3.3 | `saasMode` true (Phase 2+) | `curl -s .../env.js \| grep -i saas` or inspect in browser | ☐ |
| 3.4 | Static assets 200 | DevTools Network: main.js, styles load without 404 | ☐ |
| 3.5 | Hash routing works | Navigate to `/#/login` — no nginx 404 | ☐ |

---

## 4. Fineract API (default tenant)

| # | Check | Command / action | Pass |
|---|-------|------------------|------|
| 4.1 | Authentication | Login as staging admin; obtain session / basic auth | ☐ |
| 4.2 | Clients list | `GET /fineract-provider/api/v1/clients?limit=1` with `Fineract-Platform-TenantId: default` | ☐ |
| 4.3 | Create client (smoke) | Create and delete a test client | ☐ |
| 4.4 | Content upload path writable | Upload document; file appears under `/var/lib/somimas/fineract-content` | ☐ |

---

## 5. SaaS control plane (Phase 2+)

| # | Check | Command / action | Pass |
|---|-------|------------------|------|
| 5.1 | Public plans | `GET /saas-api/public/plans` → JSON array | ☐ |
| 5.2 | Signup | `POST /saas-api/public/signup` with test org → `202` + `jobId` | ☐ |
| 5.3 | Provisioning completes | Poll `GET /saas-api/organizations/{id}/provisioning` → `COMPLETE` | ☐ |
| 5.4 | New tenant isolated | Login to new org; Fineract tenant header matches slug | ☐ |
| 5.5 | Trial subscription | `GET /saas-api/organizations/{id}/subscription` → `TRIAL` | ☐ |
| 5.6 | Platform admin | Suspend org; Fineract access blocked for that tenant | ☐ |

---

## 6. Billing & webhooks (Phase 3+)

| # | Check | Command / action | Pass |
|---|-------|------------------|------|
| 6.1 | Invoice generated | Upgrade plan → invoice row in DB | ☐ |
| 6.2 | Checkout session | `POST` payment checkout returns redirect URL | ☐ |
| 6.3 | Webhook idempotency | Replay same Flutterwave event → single payment row | ☐ |
| 6.4 | Invalid signature rejected | POST webhook without hash → `401/403` | ☐ |
| 6.5 | Bank deposit flow | Submit deposit + platform approve → subscription active | ☐ |

---

## 7. Operations

| # | Check | Command / action | Pass |
|---|-------|------------------|------|
| 7.1 | Manual backup | `sudo /opt/somimas/scripts/backup.sh` → new dir under `/var/backups/somimas/` | ☐ |
| 7.2 | Backup includes tenant DBs | Manifest lists `somimas_*` databases | ☐ |
| 7.3 | Restore drill (staging) | `restore.sh --from <backup>` on clone; health checks pass | ☐ |
| 7.4 | Rollback drill | `rollback.sh`; previous release serves traffic | ☐ |
| 7.5 | Cron backup scheduled | `cat /etc/cron.d/somimas-backup` | ☐ |

---

## 8. Security smoke

| # | Check | Command / action | Pass |
|---|-------|------------------|------|
| 8.1 | DB users least-privilege | `somimas_control` cannot `SELECT` from `fineract_default` | ☐ |
| 8.2 | Env files not world-readable | `stat -c '%a' /etc/somimas/*.env` → `640` | ☐ |
| 8.3 | Actuator not exposing secrets | `/actuator/env` not public (or disabled) | ☐ |
| 8.4 | Rate limiting active | Burst 50 rapid API calls → some `503/429` from nginx | ☐ |

---

## Sign-off

| Role | Name | Date | Approved |
|------|------|------|----------|
| Engineering | | | ☐ |
| Operations | | | ☐ |
| Product | | | ☐ |

**Notes:**

_______________________________________________

_______________________________________________
