# Seatbook – implementation plan (Spring Boot 3.4, Azure SQL, local TTL cache)

## Phases (≈1 day)
1. **Skeleton + DB** (1h): pom, `application.yml` (your virtual-thread/Hikari/Tomcat config), Flyway `V1__schema.sql`, Azure SQL firewall + `connectRetryCount` for auto-pause.
2. **Atomic core** (2h): `BookingRepository` SQL + `ReservationService.reserveTx`. Get the hot-seat test green locally before anything else.
3. **API + auth** (1h): controllers, JWT (HS256, `sub` = user, `role` = ADMIN/USER), exception mapping (declines = 409, never 5xx).
4. **Caches** (1h): `LocalCaches` (Caffeine TTL) – refuse-early, replay, immutable show meta, 1s show state.
5. **Observe** (1.5h): Prometheus metrics, liveness/readiness, JSON logs + request id, reconciler.
6. **Deploy** (1.5h): Dockerfile, Render/Railway/Fly, burst against the live URL, fix what breaks.
7. **Write-up** (0.5h): `WRITEUP.md` in your own words.

## Core design decisions
| Concern | Mechanism |
|---|---|
| No double-sell | `UPDATE seats … WHERE status='available' AND seat_label IN (…)`; row count must equal requested count, else rollback. The DB decides, not Java. |
| Multi-seat | All-or-nothing in one tx. Seats are sorted; lock order is always reservation row → seats (PK order) → quota row, in reserve and cancel. Deadlock victims (1205) are retried (≤5). |
| Per-user limit | `user_show_quota` + guarded `MERGE … WHEN MATCHED AND active+n<=limit`; 0 rows ⇒ decline. Same tx as the seat claim, so a rollback gives back both. |
| Idempotency | `UNIQUE(user_id, idempotency_key)` on `reservations`, inserted first in the tx via `saveAndFlush`. Concurrent duplicates block on the unique key, surface as `DataIntegrityViolationException`, and are turned into a replay if the row exists. Stored `request_hash` ⇒ same key + different seats ⇒ 409. Replay returns 200. |
| Hold model | Explicit `POST /reservations/{id}/cancel` (owner only). Seats go straight to `confirmed`, so `held` is always 0; invariant still `available+held+confirmed==total`. Release is guarded on `reservation_id` AND `status='confirmed'`. |
| Identity | Only `jwt.getSubject()`. Request body has no user field; extra fields are ignored. |
| Local TTL cache | **Seat locks only** (`SeatLockCache`, Caffeine). Entry = one seat touched in the last few seconds: IN_FLIGHT (TTL 90s, > Hikari 60s wait) while a reserve runs, CONFIRMED (TTL 5s) after commit, hard cap 100k entries. Non-blocking `putIfAbsent` in sorted seat order, all-or-nothing release on conflict, so no deadlock. Same idempotency identity (user+key) bypasses the lock so retries reach the DB and replay. It is admission control, not truth: the guarded UPDATE still decides, and a lost entry only costs one extra losing DB attempt. No show, seat-list, reservation or replay data is cached. |

## Capacity: what your config does and does not buy you
- Virtual threads + `max-connections 35000` mean 20k in-flight requests cost almost nothing on the app side.
- The ceiling is **10 DB connections × round-trip latency to Azure SQL**. A reserve tx is ~5 round trips (insert reservation, claim, quota, commit, + begin). At ~20 ms cross-region RTT that is ~100 ms per tx per connection ⇒ ~100 tx/s ⇒ ~200 s to drain 20k *if every request reached the DB*.
- So the plan keeps the hot-seat burst off the DB with the seat lock (losers are refused in memory); GET /shows/{id} uses single-flight (concurrent reads share one query, nothing retained); the metrics gauge is in-memory. Costs of not caching show data: each reserve does one extra PK read of the show row inside its transaction. Remaining levers, in order: put the app in the **same Azure region** as the DB; avoid auto-pause/Basic tier (use ≥ S3/vCore); move the reserve tx into one stored procedure (1 round trip); coalesce same-seat requests in-process.
- `connection-timeout: 60000` means saturation shows up as *latency*, not errors, but Tomcat's 30s `connection-timeout` and the grader's own client timeout may fire first. Watch `hikaricp_connections_pending` and p99 `http_server_requests`.
- Readiness uses a separate 1-connection pool so the probe is never queued behind the burst.

## Risks / assumptions to confirm
- Token format for graders is unspecified in the brief; I assumed HS256 JWT + `/auth/dev-token`. Confirm how they will authenticate.
- Decline status codes: all declines are **409** with a `reason` field (seat-taken, per-user-limit, idempotency-key-reused). Replay is **200** (first success is the only 201).
- Single instance assumed: the seat locks and the `seats_available` gauge are per-process. With several instances the locks only reduce DB load; correctness still comes from the DB (the reconciler corrects the gauge).
- A request refused by the seat lock gets 409 seat-taken even if the in-flight holder later fails (e.g. over its per-user limit). That matches the brief's "actively held" wording; the burst's hot-seat check still sees exactly one 201.
- Declined attempts are not stored, so a later request with the same key can succeed.
- Not compiled or run in the sandbox that generated this (no Maven access). Only `scripts/Burst.java` was compile-checked. Expect to fix small compile errors.

## JPA rules applied
- `open-in-view: false`; all DB access sits in `@Transactional` beans (`BookingTx`, `ShowStore`) that do only DB work. Caches, metrics, logging, retries and backoff live in non-transactional services, so a connection is held for the statements only and every retry is a fresh transaction.
- Reads are `@Transactional(readOnly = true)` and return records via JPQL constructor expressions (`ShowRow`, `SeatRow`, `ReservationRow`). Seats are never loaded as entities on the hot path.
- The atomic decision stays a guarded bulk `UPDATE` (JPQL) with a row-count check. Only the quota `MERGE` is native SQL.
- `ReservationEntity` implements `Persistable` so `save` is a plain INSERT, not merge (which would SELECT first).
- Show creation persists via `EntityManager`, flushing and clearing every 1000 seats; Hibernate sends them in JDBC batches of 50 (`useBulkCopyForBatchInsert` lets the driver bulk-copy each batch).
- N+1: no associations are mapped, so there is nothing to lazy-load. Run with `SPRING_PROFILES_ACTIVE=dev` (show-sql + Hibernate statistics) and check statements per request: reserve should be ~4 (insert, claim, quota, plus commit), GET show 1, cancel 4.
- Extra settings beyond your snippet: `hikari.auto-commit=false` + `hibernate.connection.provider_disables_autocommit=true` (avoids two extra round trips per transaction on the SQL Server driver) and `sendStringParametersAsUnicode=false` with VARCHAR key columns (keeps index seeks; NVARCHAR parameters against VARCHAR columns force a scan).
