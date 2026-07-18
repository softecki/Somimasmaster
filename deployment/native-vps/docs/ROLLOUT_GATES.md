# Somimas Rollout Gates

Phased feature flags for rolling out the SaaS control plane alongside Fineract on a native Ubuntu VPS. Each phase has explicit entry/exit criteria. Do not advance until the gate checklist passes on staging.

## Phase 0 — Infrastructure (no traffic)

**Goal:** Server bootstrapped, databases created, TLS issued, services installed but not exposed to users.

| Flag / setting | Value |
|----------------|-------|
| Nginx site enabled | `false` or basic auth |
| `fineract.service` | `enabled`, not started until deploy |
| `somimas-control-plane.service` | `enabled`, not started until deploy |
| Public DNS | Points to VPS |

**Exit gate:**
- [ ] `bootstrap-ubuntu.sh` completed without errors
- [ ] MariaDB bootstrap applied; `somimas_control`, `fineract_tenants`, `fineract_default` exist
- [ ] UFW allows only SSH + Nginx
- [ ] Certbot certificate valid for `APP_DOMAIN`

---

## Phase 1 — Core banking only (Fineract)

**Goal:** Existing Fineract tenant operational; SaaS UI hidden.

| Flag / setting | Value |
|----------------|-------|
| `SAAS_MODE` in `env.js` | `false` |
| Control plane | stopped or health-only |
| Nginx `/saas-api/` | `return 404` (optional) |

**Exit gate:**
- [ ] Fineract health: `GET /fineract-provider/actuator/health` → `UP`
- [ ] Login via Angular with tenant `default` succeeds
- [ ] Sample loan/client CRUD in staging tenant
- [ ] Nightly backup job runs and produces restorable dump

---

## Phase 2 — SaaS signup & provisioning (internal)

**Goal:** Control plane live for staff/test orgs; payments disabled.

| Flag / setting | Value |
|----------------|-------|
| `SAAS_MODE` | `true` |
| `SOMIMAS_AUTH_MODE` | `local` |
| `SOMIMAS_FLUTTERWAVE_*` | empty |
| Public signup | restricted to allowlisted emails or VPN |

**Exit gate:**
- [ ] `POST /saas-api/public/signup` returns `202` with `jobId`
- [ ] Provisioning worker completes: org → `ACTIVE`, tenant DB `somimas_<slug>` created
- [ ] New org can log in and reach isolated Fineract tenant
- [ ] Trial subscription created with correct `SOMIMAS_TRIAL_DAYS`
- [ ] Platform admin can list/suspend orgs

---

## Phase 3 — Billing & payments (staging)

**Goal:** Invoices and Flutterwave checkout validated end-to-end in sandbox.

| Flag / setting | Value |
|----------------|-------|
| `SOMIMAS_FLUTTERWAVE_*` | sandbox keys |
| Webhook URL | `https://APP_DOMAIN/saas-api/webhooks/flutterwave` |
| Bank deposits | manual review enabled |

**Exit gate:**
- [ ] Checkout session creates pending payment
- [ ] Webhook with valid `secret-hash` marks invoice paid (idempotent on retry)
- [ ] Duplicate webhook does not double-credit
- [ ] Bank deposit submission + platform approval flow works
- [ ] Subscription transitions: `TRIAL` → `ACTIVE` → `PAST_DUE` → `SUSPENDED`

---

## Phase 4 — Production cutover

**Goal:** Public SaaS launch on production domain.

| Flag / setting | Value |
|----------------|-------|
| `APP_DOMAIN` | production hostname |
| Flutterwave | live keys in `/etc/somimas/control-plane.env` |
| Monitoring | health checks + log alerts configured |
| Backups | verified restore drill within last 7 days |

**Exit gate:**
- [ ] All items in [VERIFICATION_CHECKLIST.md](VERIFICATION_CHECKLIST.md) pass on staging
- [ ] Rollback drill: `rollback.sh` restores previous release in < 5 minutes
- [ ] On-call runbook acknowledged
- [ ] RPO/RTO documented (default: RPO 24h via nightly backup, RTO 60 min)

---

## Emergency rollback flags

| Scenario | Action |
|----------|--------|
| Bad release | `sudo /opt/somimas/scripts/rollback.sh` |
| Control plane outage | Stop `somimas-control-plane`; set `SAAS_MODE=false` in env.js, redeploy frontend only |
| Payment incident | Clear Flutterwave keys; block `/saas-api/webhooks/` at Nginx |
| Data corruption | Stop services; `restore.sh --from <backup>` on staging first |

---

## Configuration reference

| File | Purpose |
|------|---------|
| `/etc/somimas/fineract.env` | Fineract JVM env |
| `/etc/somimas/control-plane.env` | Control plane + billing |
| `/var/www/somimas/current/assets/env.js` | Angular runtime flags |
| `/etc/nginx/sites-available/somimas.conf` | Edge routing |
