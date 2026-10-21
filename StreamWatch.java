import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Run with Java 17+. All state and painting stay on Swing's event thread. */
public final class StreamWatch extends JPanel {
    static final Color BG = new Color(15, 25, 31), PANEL = new Color(24, 39, 46);
    static final Color INK = new Color(229, 233, 217), MUTED = new Color(151, 174, 175);
    static final Color GREEN = new Color(175, 230, 113), ORANGE = new Color(255, 151, 113);
    final CardLayout pages = new CardLayout();
    final JPanel body = new JPanel(pages);
    final JSpinner window = spinner(60, 10, 500, 10), k = spinner(5, 1, 20, 1);
    final JSpinner threshold = new JSpinner(new SpinnerNumberModel(3.0, 1.0, 10.0, .5));
    final JTextField entity = new JTextField("sensor-A", 9), value = new JTextField("145", 7);
    final JTextArea ranking = area(), hashing = area(), feed = area();
    final JLabel metrics = label("", 17, INK), status = label("READY / press Start stream", 12, GREEN);
    final JButton play = button("▶ Start stream");
    final Signal chart = new Signal();
    final Random random = new Random(13);
    final javax.swing.Timer timer;
    Detector detector = new Detector(60, 5, 3);
    int tick;

    public StreamWatch() {
        setLayout(new BorderLayout(0, 18)); setBackground(BG); setBorder(new EmptyBorder(22, 28, 20, 28));
        JPanel header = row();
        header.add(label("▦ STREAMWATCH", 26, GREEN));
        header.add(label(" /  GROUP 13   •   DSA MICRO PROJECT", 12, MUTED));
        JButton live = button("01 / Observatory"), about = button("02 / Field notes");
        header.add(live); header.add(about); add(header, BorderLayout.NORTH);
        body.setOpaque(false); body.add(dashboard(), "live"); body.add(about(), "about");
        add(body, BorderLayout.CENTER);
        add(label("LOCAL STREAM LAB     /     JAVA 17     /     SLIDING WINDOW → HASHMAP → HEAP", 11, MUTED), BorderLayout.SOUTH);
        live.addActionListener(e -> pages.show(body, "live"));
        about.addActionListener(e -> pages.show(body, "about"));
        timer = new javax.swing.Timer(500, e -> generate(false));
        play.addActionListener(e -> { if (timer.isRunning()) pause(); else {
            timer.start(); play.setText("Ⅱ Pause stream"); status.setText("LIVE / simulated sensor telemetry");
        }});
        refresh();
    }
    JPanel dashboard() {
        JPanel page = new JPanel(new BorderLayout(0, 14)); page.setOpaque(false);
        JPanel north = new JPanel(new GridLayout(0, 1, 0, 9)); north.setOpaque(false);
        north.add(label("Small window. Sharp instincts.", 32, INK));
        north.add(label("Watch incoming signals, catch unusual values, and inspect the structures behind every alert.", 13, MUTED));
        JPanel controls = row();
        controls.add(play);
        JButton step = button("+ One event"), spike = button("↗ Inject spike"), reset = button("Apply & reset");
        controls.add(step); controls.add(spike); controls.add(label("Window", 12, MUTED)); controls.add(window);
        controls.add(label("Top K", 12, MUTED)); controls.add(k);
        controls.add(label("Z threshold", 12, MUTED)); controls.add(threshold); controls.add(reset);
        step.addActionListener(e -> generate(false)); spike.addActionListener(e -> generate(true));
        reset.addActionListener(e -> {
            try {
                window.commitEdit(); k.commitEdit(); threshold.commitEdit();
                Detector next = new Detector((int) window.getValue(), (int) k.getValue(), ((Number) threshold.getValue()).doubleValue());
                pause(); detector = next; tick = 0; random.setSeed(13); refresh(); status.setText("RESET / settings applied");
            } catch (Exception ex) { error("Use valid settings. K must not exceed the window size."); }
        });
        north.add(controls); north.add(metrics); page.add(north, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(14, 14)); center.setOpaque(false);
        JPanel graph = new JPanel(new BorderLayout()); graph.setBackground(PANEL);
        graph.setBorder(new EmptyBorder(15, 18, 12, 18));
        graph.add(label("01 / SIGNAL WINDOW                              ● normal     ◆ anomaly", 13, GREEN), BorderLayout.NORTH);
        graph.add(chart, BorderLayout.CENTER);
        graph.add(label("Arrival order →    •    Scores are fixed at arrival    •    Five prior readings per entity to warm up", 11, MUTED), BorderLayout.SOUTH);
        center.add(graph, BorderLayout.CENTER);
        JPanel columns = new JPanel(new GridLayout(1, 3, 14, 0)); columns.setOpaque(false);
        columns.setPreferredSize(new Dimension(900, 190));
        columns.add(box("02 / TOP-K • CURRENT WINDOW", ranking));
        columns.add(box("03 / HASH TABLE • ENTITY STATE", hashing));
        columns.add(box("04 / RECENT ARRIVALS", feed)); center.add(columns, BorderLayout.SOUTH);
        page.add(center, BorderLayout.CENTER);
        JPanel bottom = row(); bottom.add(label("Manual input", 12, MUTED)); bottom.add(entity); bottom.add(value);
        JButton submit = button("Add reading →"); bottom.add(submit); bottom.add(status);
        submit.addActionListener(e -> {
            try { detector.accept(entity.getText().trim(), Double.parseDouble(value.getText().trim())); refresh();
                status.setText("ACCEPTED / manual reading");
            } catch (IllegalArgumentException ex) { error(ex.getMessage()); }
        });
        page.add(bottom, BorderLayout.SOUTH); return page;
    }
    JPanel about() {
        JPanel panel = new JPanel(new BorderLayout(0, 16)); panel.setOpaque(false);
        panel.add(label("Field notes / How the detector thinks", 30, INK), BorderLayout.NORTH);
        JTextArea notes = area(); notes.setLineWrap(true); notes.setWrapStyleWord(true);
        notes.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        notes.setText("REAL-TIME ANOMALY DETECTION IN BIG DATA STREAMS\n\n" +
            "Continuously arriving sensor readings are difficult to inspect by repeatedly scanning all historical data. StreamWatch processes readings sequentially and retains only a fixed-size recent window, making memory use independent of the stream's total length. The goal is to demonstrate a small, explainable anomaly detector using the three data structures in the project report.\n\n" +
            "For each entity, the detector compares an incoming value with its previous readings in the window. A large absolute Z-score flags a potential anomaly; a bounded min-heap selects the most significant active alerts. This laboratory supports generated and manual readings, live visualization, adjustable settings, and inspection of frequency and statistical state. It is an educational prototype, not a production big-data platform.\n\n" +
            "THE THREE DATA STRUCTURES\n" +
            "01  Sliding window / ArrayDeque — append newest; evict oldest at W records.\n" +
            "02  HashMap — entity → count, mean and squared deviation (M2).\n" +
            "03  PriorityQueue min-heap — retain the K largest anomaly scores.\n\n" +
            "ALGORITHMS & THE IMPORTANT DETAILS\n" +
            "• Sequential stream processing and FIFO expiration.\n" +
            "• Welford add/remove updates for rolling mean and variance.\n" +
            "• Absolute Z-score = |value − mean| / max(standard deviation, 1).\n" +
            "• Five prior observations of an entity are required before scoring.\n" +
            "• Score BEFORE insertion; then evict, update the hash state, and append.\n" +
            "• Snapshot Top-K: scan active alerts, keep a min-heap of at most K.\n" +
            "• Arrival scores remain fixed; expired alerts disappear from the ranking.\n\n" +
            "COMPLEXITY / HONEST ACCOUNTING\n" +
            "Ingestion: O(1) amortized/average. Memory: O(W + U + K).\n" +
            "Heap candidate: O(log K). Exact current-window ranking: O(W log K).\n" +
            "Ranking is rebuilt for display so evicted winners can be replaced correctly.\n" +
            "The report's O(log K) applies to one heap update, not the complete snapshot.\n\n" +
            "PROJECT AUTHORS / GROUP 13\n" +
            "Pavankumar Thatipamula / 61     Yash Satam / 54     Neev Patel / 40\n" +
            "Guide: Mrs Bhagyashree Patil\n" +
            "Artificial Intelligence & Data Science • Shah and Anchor Kutchhi Engineering College\n" +
            "Semester III • ADCOR2PC202 • Academic year 2026–27\n\n" +
            "DEMO TIP  Start the stream, wait for warm-up, then inject a spike.\n" +
            "Pause to inspect the heap ranking and hash counts. Change settings with Apply & reset.");
        JScrollPane scroll = new JScrollPane(notes); scroll.setBorder(new EmptyBorder(20, 20, 20, 20));
        scroll.setBackground(PANEL); panel.add(scroll); return panel;
    }
    void generate(boolean spike) {
        String name = "sensor-" + (char) ('A' + tick++ % 3);
        double number = 50 + random.nextGaussian() * 3 + (spike ? 65 : 0);
        Detector.Event event = detector.accept(name, number); refresh();
        status.setText(!event.ready() ? "WARM-UP / need 5 prior readings per entity" :
            event.anomaly() ? "ALERT / " + name + " exceeded threshold" : "MONITORING / reading accepted");
    }
    void pause() { timer.stop(); play.setText("▶ Start stream"); status.setText("PAUSED / manual controls available"); }
    void error(String message) { JOptionPane.showMessageDialog(this, message, "Check input", JOptionPane.WARNING_MESSAGE); }
    void refresh() {
        metrics.setText(String.format("%06d EVENTS     /     %03d SESSION ALERTS     /     %d / %d IN WINDOW     /     %d ENTITIES",
            detector.processed(), detector.detected(), detector.size(), detector.capacity(), detector.unique()));
        StringBuilder top = new StringBuilder(" RANK  ENTITY        Z-SCORE\n\n"); int rank = 1;
        for (Detector.Event e : detector.top()) top.append(String.format(Locale.ROOT, " %02d    %-12s %6.2f\n", rank++, e.entity(), e.score()));
        if (rank == 1) top.append(" No active anomalies.\n Let the baseline warm up."); ranking.setText(top.toString());
        StringBuilder hash = new StringBuilder(" ENTITY       COUNT     MEAN      STD\n\n");
        detector.entityRows().forEach((name, stats) -> hash.append(" " + name + "  " + stats + "\n")); hashing.setText(hash.toString());
        StringBuilder log = new StringBuilder(" ID    ENTITY       VALUE   STATE\n\n"); var events = detector.events();
        for (int i = events.size() - 1; i >= Math.max(0, events.size() - 7); i--) {
            var e = events.get(i); log.append(String.format(Locale.ROOT, " %04d  %-11s %6.1f  %s\n", e.id(), e.entity(), e.value(), !e.ready() ? "warm" : e.anomaly() ? "ALERT" : "ok"));
        }
        feed.setText(log.toString()); chart.repaint();
    }
    static JLabel label(String s, int size, Color color) { JLabel l = new JLabel(s); l.setForeground(color); l.setFont(new Font(Font.MONOSPACED, Font.BOLD, size)); return l; }
    static JPanel row() { JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3)); p.setOpaque(false); return p; }
    static JButton button(String text) { JButton b = new JButton(text); b.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12)); b.setFocusPainted(true); return b; }
    static JSpinner spinner(int v, int min, int max, int step) { return new JSpinner(new SpinnerNumberModel(v, min, max, step)); }
    static JTextArea area() { JTextArea a = new JTextArea(); a.setEditable(false); a.setBackground(PANEL); a.setForeground(INK); a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); a.setMargin(new Insets(12, 4, 4, 4)); return a; }
    static JPanel box(String title, JTextArea a) { JPanel p = new JPanel(new BorderLayout()); p.setBackground(PANEL); p.setBorder(new EmptyBorder(12, 12, 8, 12)); p.add(label(title, 12, GREEN), BorderLayout.NORTH); JScrollPane s = new JScrollPane(a); s.setBorder(null); p.add(s); return p; }
    final class Signal extends JPanel {
        Signal() { setOpaque(false); setPreferredSize(new Dimension(950, 240)); }
        protected void paintComponent(Graphics raw) {
            super.paintComponent(raw); Graphics2D g = (Graphics2D) raw.create();
            int w = getWidth(), h = getHeight(), left = 55, right = w - 30, top = 35, bottom = h - 35;
            var events = detector.events(); double min = 30, max = 75;
            for (var e : events) { min = Math.min(min, e.value() - 5); max = Math.max(max, e.value() + 5); }
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            for (int i = 0; i <= 4; i++) { int y = top + (bottom - top) * i / 4;
                g.setColor(new Color(43, 60, 65)); g.drawLine(left, y, right, y); g.setColor(MUTED);
                g.drawString(String.format(Locale.ROOT, "%.0f", max - (max - min) * i / 4), 3, y + 4); }
            int px = 0, py = 0;
            for (int i = 0; i < events.size(); i++) {
                var e = events.get(i); int x = left + i * (right - left) / Math.max(1, detector.capacity() - 1);
                int y = bottom - (int) ((e.value() - min) / (max - min) * (bottom - top));
                if (i > 0) { g.setColor(new Color(91, 136, 111)); g.drawLine(px, py, x, y); }
                g.setColor(e.anomaly() ? ORANGE : GREEN); int r = e.anomaly() ? 5 : 3; g.fillRect(x-r, y-r, r*2, r*2); px=x; py=y;
            }
            if (events.isEmpty()) { g.setColor(MUTED); g.drawString("Awaiting first signal. Start the stream or add a reading.", left + 24, top + 60); }
            // Tiny pixel sentinel walks with each arriving event; pausing freezes it.
            int sx = right - 45, sy = top + (tick % 2) * 3;
            g.setColor(GREEN); g.fillRect(sx, sy, 28, 20); g.fillRect(sx+4, sy-6, 20, 6);
            g.fillRect(sx+2, sy+20, 7, 7); g.fillRect(sx+19, sy+20, 7, 7);
            g.setColor(BG); g.fillRect(sx+5, sy+5, 5, 5); g.fillRect(sx+18, sy+5, 5, 5); g.dispose();
        }
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            StreamWatch app = new StreamWatch();
            if (args.length == 2 && args[0].equals("--preview")) {
                for (int i=0; i<45; i++) app.generate(i==25 || i==38);
                app.setSize(1260, 850); layoutAll(app);
                BufferedImage image = new BufferedImage(1260, 850, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics(); app.printAll(graphics); graphics.dispose();
                try { ImageIO.write(image, "png", new File(args[1])); } catch (Exception ex) { throw new RuntimeException(ex); }
                return;
            }
            JFrame frame = new JFrame("StreamWatch / Group 13"); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(app); frame.setMinimumSize(new Dimension(1180, 800)); frame.setSize(1300, 900);
            frame.setLocationRelativeTo(null); frame.setVisible(true);
        });
    }
    static void layoutAll(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container container) layoutAll(container); }
}
