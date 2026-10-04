# WRITEUP  (fill in your own words; you will be asked to extend this live)

## 1. The atomic decision
<exact mechanism, why it is race-free, multi-seat ordering / deadlock handling>

## 2. Idempotency
<where the key lives, how exactly-once is enforced, same-key-different-body>

## 3. Holds & expiry
<explicit cancel model, why release can't resurrect a re-sold seat>

## 4. Consistency vs availability under a partition
<what happens when Azure SQL is unreachable: readiness fails closed, requests fail, caches never grant>

## 5. Observability – what pages me at 2am
<5xx rate, hikaricp pending, reconcile drift > 0, readiness flapping, p99 latency>

## 6. AI usage (directed vs decided)
<be specific and honest>

## 7. What I'd do next
<stored procedure, multi-instance, expiring holds, load-shedding>
