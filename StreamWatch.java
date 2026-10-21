import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** Tiny Java web server; the browser draws the UI, Detector does all analysis. */
public final class StreamWatch {
    static final Map<String, Session> sessions = new HashMap<>();
    static final class Session {
        Detector detector = new Detector(60, 5, 3);
        Random random = new Random(13);
        int tick, k = 5;
        double threshold = 3;
        long touched = System.currentTimeMillis();
        void generate(boolean spike) {
            String entity = "sensor-" + (char) ('A' + tick++ % 3);
            detector.accept(entity, 50 + random.nextGaussian() * 3 + (spike ? 65 : 0));
        }
        void reset(int window, int count, double limit) {
            Detector next = new Detector(window, count, limit); // Validate before mutation.
            detector = next; k = count; threshold = limit; tick = 0; random = new Random(13);
        }
    }
    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String host = System.getenv().getOrDefault("HOST", "127.0.0.1");
        byte[] page = Files.readAllBytes(Path.of("index.html"));
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", exchange -> {
            if (!exchange.getRequestMethod().equals("GET")) { send(exchange, 405, "text/plain", "Use GET"); return; }
            if (!Set.of("/", "/index.html").contains(exchange.getRequestURI().getPath())) {
                send(exchange, 404, "text/plain", "Not found"); return;
            }
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, page.length);
            try (var out = exchange.getResponseBody()) { out.write(page); }
        });
        server.createContext("/api", StreamWatch::api);
        // Serialized requests make each session update atomic without extra locking.
        server.setExecutor(Executors.newSingleThreadExecutor()); server.start();
        System.out.println("StreamWatch is ready. Open http://localhost:" + port);
    }
    static void api(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestURI().getPath().equals("/api")) { send(exchange,404,"application/json","{}"); return; }
            if (!exchange.getRequestMethod().equals("POST")) { send(exchange,405,"application/json","{}"); return; }
            if ("cross-site".equals(exchange.getRequestHeaders().getFirst("Sec-Fetch-Site"))) {
                send(exchange,403,"application/json","{}"); return;
            }
            String id = exchange.getRequestHeaders().getFirst("X-Session");
            if (id == null || !id.matches("[a-zA-Z0-9-]{16,80}")) throw new IllegalArgumentException("Invalid session ID.");
            byte[] body = exchange.getRequestBody().readNBytes(2049);
            if (body.length > 2048) throw new IllegalArgumentException("Request too large.");
            Map<String,String> data = new HashMap<>();
            for (String pair : new String(body, StandardCharsets.UTF_8).split("&")) {
                String[] fields = pair.split("=", 2);
                if (fields.length == 2) data.put(URLDecoder.decode(fields[0], StandardCharsets.UTF_8), URLDecoder.decode(fields[1], StandardCharsets.UTF_8));
            }
            long now = System.currentTimeMillis();
            sessions.values().removeIf(session -> now - session.touched > 1_800_000);
            if (!sessions.containsKey(id) && sessions.size() >= 128) {
                send(exchange,503,"application/json","{\"error\":\"The lab is full. Please try again later.\"}"); return;
            }
            Session session = sessions.computeIfAbsent(id, ignored -> new Session()); session.touched = now;
            switch (data.getOrDefault("action", "state")) {
                case "state" -> { }
                case "step" -> session.generate(false);
                case "spike" -> session.generate(true);
                case "manual" -> session.detector.accept(data.get("entity"), Double.parseDouble(data.getOrDefault("value", "NaN")));
                case "reset" -> session.reset(Integer.parseInt(data.getOrDefault("window", "60")),
                    Integer.parseInt(data.getOrDefault("k", "5")), Double.parseDouble(data.getOrDefault("threshold", "3")));
                default -> throw new IllegalArgumentException("Unknown action.");
            }
            send(exchange,200,"application/json", snapshot(session));
        } catch (IllegalArgumentException ex) {
            send(exchange,400,"application/json","{\"error\":" + quote(ex.getMessage()) + "}");
        } catch (Exception ex) {
            send(exchange,500,"application/json","{\"error\":\"Server error. Please retry.\"}");
        }
    }
    static String snapshot(Session session) {
        Detector d = session.detector;
        String hash = d.entityRows().entrySet().stream().map(entry -> {
            String[] stats = entry.getValue().trim().split("\\s+");
            return "{\"entity\":" + quote(entry.getKey()) + ",\"count\":" + stats[0] +
                ",\"mean\":" + stats[1] + ",\"std\":" + stats[2] + "}";
        }).collect(Collectors.joining(","));
        return "{\"processed\":" + d.processed() + ",\"detected\":" + d.detected() +
            ",\"capacity\":" + d.capacity() + ",\"k\":" + session.k + ",\"threshold\":" + session.threshold +
            ",\"events\":" + events(d.events()) + ",\"top\":" + events(d.top()) + ",\"entities\":[" + hash + "]}";
    }
    static String events(List<Detector.Event> events) {
        return events.stream().map(e -> String.format(Locale.ROOT,
            "{\"id\":%d,\"entity\":%s,\"value\":%.6f,\"score\":%.6f,\"ready\":%s,\"anomaly\":%s}",
            e.id(), quote(e.entity()), e.value(), e.score(), e.ready(), e.anomaly())).collect(Collectors.joining(",", "[", "]"));
    }
    static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
    static void send(HttpExchange exchange, int code, String type, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(code, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }
}
