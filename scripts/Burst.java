import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

/** Usage: java scripts/Burst.java <BASE_URL> <ADMIN_SECRET>   (JDK 21, no dependencies) */
public class Burst {
    static String base; static HttpClient http;
    static final Map<String, String> tokens = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<String, LongAdder> dist = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<String, AtomicInteger> winnersPerSeat = new ConcurrentHashMap<>();
    static final AtomicInteger confirmedSeats = new AtomicInteger();

    public static void main(String[] a) throws Exception {
        base = a.length > 0 ? a[0] : "http://localhost:8080";
        String admin = a.length > 1 ? a[1] : "change-me";
        http = HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(30)).build();
        int seatsN = 2000, hot = 5, perHot = 500, stampede = 20000, users = 5000;

        List<String> seats = new ArrayList<>();
        for (int i = 1; i <= seatsN; i++) seats.add("S" + i);
        String adminTok = mint("admin", "ADMIN", admin);
        var res = send("POST", "/shows", adminTok, null, "{\"name\":\"burst-" + System.currentTimeMillis()
                + "\",\"seats\":[" + quoted(seats) + "],\"price_paise\":25000}");
        String showId = grab(res[1], "id");
        System.out.println("show " + showId + " seats=" + seatsN);

        // Phase 1: hot-seat storm (perHot distinct users per hot seat, all at once)
        List<Callable<Void>> jobs = new ArrayList<>();
        CountDownLatch gate = new CountDownLatch(1);
        for (int h = 1; h <= hot; h++) for (int u = 0; u < perHot; u++) {
            String seat = "S" + h, user = "hot" + h + "u" + u;
            jobs.add(() -> { gate.await(); reserve(showId, user, "[\"" + seat + "\"]", "k-" + user, seat); return null; });
        }
        runAll(jobs, gate, "phase1 hot-seat storm");

        // Phase 2: on-sale stampede (random users/seats, 20% immediate retries with same key)
        jobs = new ArrayList<>(); CountDownLatch g2 = new CountDownLatch(1);
        Random rnd = new Random(42);
        for (int i = 0; i < stampede; i++) {
            String user = "u" + rnd.nextInt(users), seat = "S" + (rnd.nextInt(10) < 6 ? 1 + rnd.nextInt(20) : 1 + rnd.nextInt(seatsN));
            String key = "s-" + i; boolean retry = rnd.nextInt(5) == 0;
            jobs.add(() -> { g2.await(); reserve(showId, user, "[\"" + seat + "\"]", key, null);
                             if (retry) reserve(showId, user, "[\"" + seat + "\"]", key, null); return null; });
        }
        runAll(jobs, g2, "phase2 stampede");

        // Phase 3: per-user limit under concurrency (10 parallel reserves, free seats, limit=4)
        jobs = new ArrayList<>(); CountDownLatch g3 = new CountDownLatch(1);
        for (int i = 0; i < 10; i++) { int n = i; jobs.add(() -> { g3.await();
            reserve(showId, "limit-user", "[\"S" + (1500 + n) + "\"]", "lim-" + n, null); return null; }); }
        runAll(jobs, g3, "phase3 per-user limit");

        // Phase 4: same key, different seats => 409
        reserve(showId, "idem-user", "[\"S1900\"]", "idem-1", null);
        reserve(showId, "idem-user", "[\"S1901\"]", "idem-1", null);

        // Report
        System.out.println("\n=== outcome distribution ===");
        new TreeMap<>(dist).forEach((k, v) -> System.out.printf("%-34s %d%n", k, v.sum()));
        boolean ok = true;
        for (int h = 1; h <= hot; h++) {
            int w = winnersPerSeat.getOrDefault("S" + h, new AtomicInteger()).get();
            System.out.println("hot seat S" + h + " winners = " + w + (w == 1 ? "  OK" : "  FAIL")); ok &= w == 1;
        }
        Thread.sleep(500); // let in-flight commits settle
        String st = send("GET", "/shows/" + showId, null, null, null)[1];
        int av = Integer.parseInt(grab(st, "available")), he = Integer.parseInt(grab(st, "held")),
            co = Integer.parseInt(grab(st, "confirmed")), tot = Integer.parseInt(grab(st, "total_seats"));
        System.out.printf("%n==gate= reconciliation ===%navailable=%d held=%d confirmed=%d total=%d  sum==total: %s%n",
                av, he, co, tot, av + he + co, (av + he + co == tot) ? "OK" : "FAIL");
        System.out.println("confirmed seats seen by clients (201) = " + confirmedSeats.get() + "  matches server: "
                + (confirmedSeats.get() == co ? "OK" : "FAIL"));
        long five = dist.entrySet().stream().filter(e -> e.getKey().startsWith("5")).mapToLong(e -> e.getValue().sum()).sum();
        System.out.println("5xx = " + five + (five == 0 ? "  OK" : "  FAIL"));
        ok &= av + he + co == tot && confirmedSeats.get() == co && five == 0;
        System.out.println(ok ? "\nRESULT: PASS" : "\nRESULT: FAIL");
        System.exit(ok ? 0 : 1);
    }

    static void runAll(List<Callable<Void>> jobs, CountDownLatch gate, String name) throws Exception {
        long t = System.nanoTime();
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var j : jobs) ex.submit(j);
                Thread.sleep(100);gate.countDown();
        }
        System.out.printf("%s: %d requests in %.1fs%n", name, jobs.size(), (System.nanoTime() - t) / 1e9 - 0.5);
    }

    static void reserve(String show, String user, String seatsJson, String key, String hotSeat) {
        try {
            var r = send("POST", "/shows/" + show + "/reserve", mint(user, "USER", null), key,
                    "{\"seats\":" + seatsJson + ",\"idempotency_key\":\"" + key + "\",\"user_id\":\"spoofed\"}");
            String label = r[0];
            if (r[0].equals("409")) label += " " + grab(r[1], "reason");
            else if (r[0].equals("200")) label += " idempotent-replay";
            dist.computeIfAbsent(label, k -> new LongAdder()).increment();
            if (r[0].equals("201")) {
                confirmedSeats.addAndGet(countSeats(seatsJson));
                if (hotSeat != null) winnersPerSeat.computeIfAbsent(hotSeat, k -> new AtomicInteger()).incrementAndGet();
            }
        } catch (Exception e) {
            dist.computeIfAbsent("client-error " + e.getClass().getSimpleName(), k -> new LongAdder()).increment();
        }
    }

    static String mint(String user, String role, String adminSecret) throws Exception {
        String k = user + "|" + role;
        String t = tokens.get(k);
        if (t != null) return t;
        var b = HttpRequest.newBuilder(URI.create(base + "/auth/dev-token?user=" + user + "&role=" + role))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (adminSecret != null) b.header("X-Admin-Secret", adminSecret);
        String body = http.send(b.build(), HttpResponse.BodyHandlers.ofString()).body();
        t = grab(body, "token");
        tokens.put(k, t);
        return t;
    }

    static String[] send(String method, String path, String token, String idemKey, String json) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (idemKey != null) b.header("Idempotency-Key", idemKey);
        b.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        var r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new String[]{String.valueOf(r.statusCode()), r.body()};
    }

    static String grab(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"?([^\",}]*)").matcher(json);
        return m.find() ? m.group(1) : "";
    }
    static int countSeats(String s) { return s.split(",").length; }
    static String quoted(List<String> l) { StringJoiner j = new StringJoiner(","); l.forEach(x -> j.add("\"" + x + "\"")); return j.toString(); }
}
