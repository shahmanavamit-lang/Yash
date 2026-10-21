import java.util.*;

/** Bounded streaming state: a FIFO window, a hash table, and a Top-K min-heap. */
public final class Detector {
    public record Event(long id, String entity, double value, double score, boolean ready,
                        boolean anomaly) {}
    public static final class Stats {
        int count;
        double mean, m2;
        // Welford's update avoids subtracting two large sums to obtain variance.
        void add(double value) {
            count++;
            double delta = value - mean;
            mean += delta / count;
            m2 += delta * (value - mean);
        }
        void remove(double value) {
            if (count == 1) { count = 0; mean = m2 = 0; return; }
            double oldMean = mean;
            mean = (count * mean - value) / (count - 1);
            count--;
            m2 = Math.max(0, m2 - (value - oldMean) * (value - mean));
        }
        double deviation() { return count < 2 ? 0 : Math.sqrt(m2 / count); }
    }
    private final ArrayDeque<Event> window = new ArrayDeque<>();
    private final HashMap<String, Stats> entities = new HashMap<>();
    private static final Comparator<Event> SIGNIFICANCE =
        Comparator.comparingDouble(Event::score).thenComparingLong(Event::id);
    private final int capacity, k;
    private final double threshold;
    private long processed, detected;

    public Detector(int capacity, int k, double threshold) {
        if (capacity < 10 || capacity > 500 || k < 1 || k > capacity ||
            !Double.isFinite(threshold) || threshold < 1 || threshold > 10)
            throw new IllegalArgumentException("Window: 10–500; K: 1–window; threshold: 1–10.");
        this.capacity = capacity; this.k = k; this.threshold = threshold;
    }
    public Event accept(String entity, double value) {
        if (entity == null || !entity.matches("[A-Za-z0-9_-]{1,20}"))
            throw new IllegalArgumentException("Entity: 1–20 letters, digits, underscores or hyphens.");
        if (!Double.isFinite(value) || Math.abs(value) > 1_000_000)
            throw new IllegalArgumentException("Value must be finite and between -1,000,000 and 1,000,000.");
        // Compare against PREVIOUS observations so the incoming spike cannot hide itself.
        Stats baseline = entities.get(entity);
        boolean ready = baseline != null && baseline.count >= 5;
        double score = ready ? Math.abs(value - baseline.mean) /
            Math.max(1.0, baseline.deviation()) : 0;
        Event event = new Event(++processed, entity, value, score, ready, ready && score >= threshold);
        if (event.anomaly()) detected++;
        if (window.size() == capacity) {
            Event old = window.removeFirst();
            Stats state = entities.get(old.entity());
            state.remove(old.value());
            if (state.count == 0) entities.remove(old.entity());
        }
        window.addLast(event);
        entities.computeIfAbsent(entity, ignored -> new Stats()).add(value);
        return event;
    }
    public List<Event> top() {
        // Rebuild from the bounded window: expired records never survive in Top-K.
        // Each candidate costs O(log K); a snapshot costs O(W log K), not O(log K).
        PriorityQueue<Event> heap = new PriorityQueue<>(SIGNIFICANCE);
        for (Event event : window) if (event.anomaly()) {
            if (heap.size() < k) heap.offer(event);
            else if (SIGNIFICANCE.compare(event, heap.peek()) > 0) {
                heap.poll(); heap.offer(event);
            }
        }
        List<Event> result = new ArrayList<>();
        while (!heap.isEmpty()) result.add(heap.poll());
        Collections.reverse(result);
        return result;
    }
    public List<Event> events() { return List.copyOf(window); }
    public Map<String, String> entityRows() {
        Map<String, String> rows = new TreeMap<>(); // Presentation order only.
        entities.forEach((name, s) -> rows.put(name,
            String.format(Locale.ROOT, "%3d    %8.2f    %7.2f", s.count, s.mean, s.deviation())));
        return rows;
    }
    public long processed() { return processed; }
    public long detected() { return detected; }
    public int size() { return window.size(); }
    public int unique() { return entities.size(); }
    public int capacity() { return capacity; }
}
