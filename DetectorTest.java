import java.util.*;

/** Run with: java -ea DetectorTest. No testing dependencies required. */
public final class DetectorTest {
    static int checks;
    static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    static void invalid(Runnable action) {
        try { action.run(); throw new AssertionError("Expected invalid input rejection"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) {
        Detector d = new Detector(10, 2, 3);
        for (int i = 0; i < 5; i++) check(!d.accept("A", 50).ready(), "Warm-up");
        check(!d.accept("A", 50).anomaly(), "Constant baseline is normal");
        check(d.accept("A", 100).anomaly(), "Spike detected against prior baseline");
        check(d.top().get(0).score() == 50, "Variance floor keeps score finite");
        for (int i = 0; i < 10; i++) d.accept("B", 10);
        check(d.size() == 10 && d.unique() == 1, "Window and hash expiration");
        check(d.top().isEmpty(), "Expired alert removed from ranking");
        invalid(() -> d.accept("bad entity", 10));
        invalid(() -> d.accept(null, 10));
        invalid(() -> d.accept("A", Double.NaN));
        invalid(() -> d.accept("A", Double.POSITIVE_INFINITY));
        invalid(() -> d.accept("A", 1_000_001));
        invalid(() -> new Detector(10, 11, 3));
        invalid(() -> new Detector(10, 2, Double.NaN));
        check(d.processed() == 17, "Invalid input does not mutate state");
        // Differential check against a simple full-window reference implementation.
        Random random = new Random(52);
        Detector stream = new Detector(40, 4, 2);
        for (int i = 0; i < 2000; i++) {
            String entity = "S" + random.nextInt(5);
            double value = random.nextGaussian() * 5 + (i % 29 == 0 ? 100 : 50);
            var previous = stream.events().stream().filter(e -> e.entity().equals(entity)).toList();
            var event = stream.accept(entity, value);
            check(event.ready() == (previous.size() >= 5), "Reference warm-up");
            if (event.ready()) {
                double mean = previous.stream().mapToDouble(Detector.Event::value).average().orElseThrow();
                double variance = previous.stream().mapToDouble(e -> Math.pow(e.value() - mean, 2)).average().orElseThrow();
                double score = Math.abs(value - mean) / Math.max(1, Math.sqrt(variance));
                check(Math.abs(score - event.score()) < 1e-8, "Rolling statistics match reference");
            }
            var expected = stream.events().stream().filter(Detector.Event::anomaly)
                .sorted(Comparator.comparingDouble(Detector.Event::score).thenComparingLong(Detector.Event::id).reversed())
                .limit(4).toList();
            check(expected.equals(stream.top()), "Heap matches sorted oracle including expiration");
            check(stream.size() <= 40 && stream.unique() <= 5, "Memory is bounded");
        }
        System.out.println("PASS: " + checks + " checks (including 2,000 differential stream steps)");
    }
}
