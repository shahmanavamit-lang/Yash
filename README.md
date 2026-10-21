# StreamWatch — The Signal Observatory

A Java 17 anomaly-detection micro-project with an animated, responsive browser interface. The detector uses the report's sliding window, hash table and heap. HTML, CSS and JavaScript live together in **one `index.html` file**; Java performs all statistical calculations and anomaly ranking.

## Run locally (no dependencies)

Install JDK 17 or newer. Open a terminal **inside this project folder**:

```sh
javac *.java
java StreamWatch
```

Keep that terminal open and visit **http://localhost:8080** in your browser. Stop the server with Ctrl+C. This replaces the earlier Swing desktop interface; the command stays the same. The server reads `index.html` on startup, so restart after editing the page.

## A beginner-friendly demonstration

1. Open **Learn the ropes** for four short lessons explaining the baseline, unusual readings, settings and structures.
2. In **Observatory**, press **Start demo**. Three simulated sensors send readings near 50.
3. Wait about 10 seconds. Each sensor needs five prior readings before scoring starts.
4. **Show an unusual reading** becomes available after all three demo sensors have a baseline. It adds a deliberately high test value; inspect the orange diamond and alert ranking.
5. Pause to inspect the hash table and latest arrivals.
6. Optional controls are collapsed: **Fine-tune the experiment** explains window size, Top-K and sensitivity; **Add your own reading** accepts manual sensor values.
7. **Project notes** contains two introductory paragraphs, separate spacious tables for data structures and algorithms, complexity, scope and author credits.

Changing settings with Apply & reset clears this session. Lower thresholds are more sensitive. K changes the number of displayed alerts, not the detection rule. Small windows may never hold enough history for all three demo sensors; 60 is the recommended teaching default. Session alert totals include expired alerts, while ranking includes only current-window alerts.

## Hosting — read this before deploying

**Vercel static hosting displays the HTML interface but does not run this Java server.** The page clearly reports a disconnected detector and disables live controls when `/api` is unavailable. The tutorial and project notes remain usable; no fake JavaScript detection is substituted.

To share the fully working application online, deploy the included Dockerfile to a service that runs Java/Docker web servers. Serve both the page and `/api` from that same Java service. It listens on the hosting service's `PORT` environment variable and uses `HOST=0.0.0.0` inside the container. Use the host's HTTPS URL. You do not need separate frontend/backend hosting or CORS configuration.

Optional local Docker commands:

```sh
docker build -t streamwatch .
docker run --rm -p 8080:8080 streamwatch
```

The Dockerfile is provided for deployment; a hosted service has not been provisioned by this repository. For static-only Vercel preview, use repository root as the output directory and no build command. That preview alone cannot run detection.

## Compact structure

| File | Purpose |
| --- | --- |
| `Detector.java` | FIFO window, hash state, rolling statistics, Z-score, heap selection |
| `StreamWatch.java` | Standard-library HTTP server and JSON API; replaces the Swing UI |
| `index.html` | All browser markup, styling, animations, tutorial and controls |
| `DetectorTest.java` | 7,849 correctness checks including 2,000 randomized reference steps |
| `README.md` | Setup, teaching guide, design and limitations |
| `Dockerfile` | Optional Java hosting recipe |
| `.gitignore` | Excludes compiled classes and OS metadata |

There are only three application source files. No frameworks, package manager, external fonts, CDNs, images or third-party Java libraries are needed. The interface includes a CSS pixel sentinel, moving signal particles, a chart sweep, page transitions and tutorial tiles. Reduced-motion preferences disable decorative animations. Navigating away from the lab or hiding the tab pauses generation.

## Data and algorithms

Incoming reading → score against prior hash/window state → evict oldest if full → update hash state and append → rebuild active Top-K heap → display.

- **Sliding window / ArrayDeque:** fixed-size FIFO of the newest W events.
- **Hash table / HashMap:** sensor ID maps to count, mean and M2 (sum of squared deviations). Welford add/remove updates maintain statistics without rescanning the history.
- **Min-heap / PriorityQueue:** retain the K most significant active anomalies during each snapshot. A larger score replaces the minimum; equal scores favor newer IDs.
- **Anomaly rule:** absolute Z-score = `abs(value - mean) / max(population standard deviation, 1)`. At least five prior readings for that sensor are required. Score at or above the threshold is flagged.

Scoring precedes insertion so an outlier cannot dilute its own baseline. Arrival scores remain fixed until expiration. The one-unit standard-deviation floor handles constant baselines. Repeated extreme values can shift the baseline; rare/new sensors must warm up. This is a simple educational statistical detector, not a trained anomaly model.

## Complexity

Window updates are O(1) amortized; hashing and scoring are O(1) average. One heap candidate is O(log(K+1)); exact active-window ranking is O(W log(K+1)). Rebuilding ensures expired winners are replaced by the next eligible active candidates. O(log K) alone would not describe this complete snapshot.

Analysis memory is O(W + U + K), with U ≤ W. Display snapshots also use bounded O(W + U + K) memory. Entity presentation ordering costs O(U log U); chart drawing costs O(W). No full stream history is retained.

## Sessions and scope

Each browser tab creates an independent random session ID. Reloading starts a fresh experiment. The server keeps at most 128 sessions and drops sessions idle for 30 minutes on subsequent requests. Restarting the server clears all memory. Requests are serialized so updates cannot race. Browser generation is one request at a time and pauses on navigation/tab hiding. This is a single-instance teaching server, not a production multi-node platform; deployment should use one instance, or it would need shared session storage/sticky routing.

Inputs are validated by Java: IDs use 1–20 ASCII letters, digits, underscores or hyphens; numeric values are finite and within ±1,000,000; W is 10–500, K is 1–W and threshold is 1–10. The browser offers simpler teaching presets. The server serves only the HTML entry page and API; it does not expose project source files.

The report's functional requirements are covered: generated/manual sequential input, fixed window, hash-based state, anomaly significance and Top-K output. Java replaces its proposed Python language as requested. External feeds, CSV/JSON uploads, databases and production-scale performance are extensions, not implemented features.

## Verification

```sh
java -ea DetectorTest
```

The detector tests check warm-up, constant baselines, spikes, invalid input without mutation, expiration, bounded memory and differential statistics/ranking correctness. The browser and HTTP integration were also checked for connection, streaming, spike injection, settings/reset, manual input and independent sessions.

## Academic credits

**Group 13:** Pavankumar Thatipamula (61), Yash Satam (54), Neev Patel (40), as listed in the supplied report. Guide: Mrs Bhagyashree Patil. Artificial Intelligence & Data Science, Shah and Anchor Kutchhi Engineering College. Semester III, ADCOR2PC202, academic year 2026–27.

Report background references: Cormen et al., *Introduction to Algorithms*; Reema Thareja, *Data Structures Using C*; Leskovec, Rajaraman and Ullman, *Mining of Massive Datasets*.
