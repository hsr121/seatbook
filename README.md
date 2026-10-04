# Seatbook

Atomic seat-reservation service. Spring Boot 3.4, Java 21 (virtual threads), Spring Data JPA/Hibernate, Azure SQL, a Caffeine TTL seat-lock table (the only local cache).
Design: see `PLAN.md`. Write-up: `WRITEUP.md`.

## Run locally
```bash
docker compose up --build       # or: mvn spring-boot:run
```
Azure SQL: create the database, add a firewall rule for the app host's outbound IPs, disable auto-pause for the demo.

## Auth
`POST /auth/dev-token?user=alice` → `{token}` ; admin: add `&role=ADMIN` and header `X-Admin-Secret`.
Send `Authorization: Bearer <token>`. Identity is the token subject only.

## API
- `POST /shows` (admin) `{name, seats[], price_paise, per_user_limit?}`
- `POST /shows/{id}/reserve` `{seats[], idempotency_key}` (or `Idempotency-Key` header) → 201 / 200 replay / 409 `{reason}`
- `POST /reservations/{id}/cancel` (owner only)
- `GET /shows/{id}`
- `GET /actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus`

## Burst (one command)
```bash
make burst BASE_URL=https://<your-app> ADMIN_SECRET=<secret>
# or: java scripts/Burst.java https://<your-app> <secret>
```
Runs: hot-seat storm (5 seats × 500 users), 20k stampede with retries, per-user-limit race, same-key-different-seats. Prints outcome distribution, per-hot-seat winner count, 5xx count and the final reconciliation. Exit code 0 = PASS.

## Metrics
`reservations_confirmed_total`, `reservations_declined_total{reason}`, `reservations_cancelled_total`, `seats_available{show_id}`, `seats_reconcile_drift{show_id}`, `seatlock_rejected_total`, seat-lock cache size/stats (`cache_size{cache="seatLocks"}`), Hikari and `http_server_requests` for 5xx.
