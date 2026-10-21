# StreamWatch

A compact Java 17 desktop micro-project: **Real-Time Anomaly Detection in Big Data Streams using Sliding Window, Hashing and Heap Data Structures**. Four files, no dependencies, no build tool, no internet connection needed to run.

## Run

Install a JDK version 17 or newer. Open a terminal in this folder:

```sh
javac Detector.java StreamWatch.java DetectorTest.java
java StreamWatch
```

A graphical desktop is required. In IntelliJ, Eclipse, or VS Code, open this folder, select JDK 17+, and run `StreamWatch.main`. GitHub stores the source; this Java desktop app does not run on GitHub Pages.

## Demonstration

1. Select **Start stream**. Three simulated sensors produce one reading every 500 ms.
2. Allow at least 18 readings so every sensor has five prior observations.
3. Click **Inject spike**. Inspect the orange marker and ranked alert.
4. Pause and inspect the entity counts, means, deviations, and recent arrivals.
5. Enter a sensor ID and numeric value to add your own record.
6. Change window capacity, K, or threshold; **Apply & reset** starts a fresh session.
7. Open **02 / Field notes** for two explanatory paragraphs, algorithms, complexity, and author credits.

A small pixel sentinel moves with incoming events. Pausing freezes updates for inspection. Window settings do not take effect until Apply & reset. Counts labelled session alerts include expired alerts; the ranking only includes active-window alerts. A generated spike during warm-up is not classified until the baseline is ready.

## Files and responsibilities

| File | Responsibility |
| --- | --- |
| `Detector.java` | Sliding window, hash state, Z-score detection, bounded min-heap ranking |
| `StreamWatch.java` | Swing dashboard, signal animation, manual/generated input, information screen |
| `DetectorTest.java` | Validation, warm-up, expiration and differential correctness tests |
| `README.md` | Setup, demonstration, design and report coverage |

The UI is intentionally separate from the algorithm. Java standard-library components provide the three required data structures: `ArrayDeque`, `HashMap`, and `PriorityQueue`. Lists and presentation ordering are incidental display helpers, not additional project algorithms.

## Detection and proposed flow

Input → score using prior window hash state → evict oldest if full → update window and hash state → select active Top-K with heap → display.

The report leaves the anomaly rule unspecified. This implementation chooses a per-entity absolute Z-score. A baseline needs five prior readings of that entity. Score = `abs(value - mean) / max(population standard deviation, 1.0)`. A score at or above the configured threshold is anomalous. The one-unit floor handles constant baselines and avoids division by zero. Values must be finite and within ±1,000,000; entity IDs allow 1–20 ASCII letters, digits, underscores or hyphens.

Scoring precedes insertion so an outlier cannot dilute its own baseline. Welford's incremental and removal equations maintain count, mean and M2. Once classified, a record keeps its arrival-time score until it expires; historical records are not reclassified. Repeated extreme values can change the baseline, a normal limitation of this simple statistical model. Rare entities need sufficient warm-up and are not automatically treated as anomalous.

For each display snapshot, scan the active window and insert anomalous records into a min-heap of at most K elements. When full, replace its minimum only for a more significant candidate. Equal scores favor newer record IDs. Polling and reversing produces descending rank. Rebuilding from the window ensures expired winners are removed and previously unranked candidates can take their place.

## Complexity

- Window insertion/eviction: O(1) amortized; hash updates and scoring: O(1) average.
- One heap candidate: O(log K); complete exact current-window ranking: O(W log K), plus O(K log K) extraction, bounded by O(W log K) when K ≤ W (use log(K+1) for K=1).
- Persistent detector state: O(W + U); temporary heap: O(K). Display snapshots also use O(W + U + K). U ≤ W.
- Display orders entities in O(U log U) and draws the signal in O(W).

The report's expected O(log K) heap cost describes an individual update. It does not include maintaining exact Top-K under expiration. This project explicitly reports the additional bounded-window scan rather than claiming O(log K) for the complete dashboard update.

## Report coverage

Problem and motivation: avoid retaining and repeatedly processing the full stream history. Aim and objectives: implement and explain recent-window analysis, fast entity tracking, anomaly significance and prioritized output. Proposed system and functional requirements: generated/manual stream input, fixed-size window, hash frequency/statistical state, anomaly detection and Top-K output. Expected outcomes: working demonstration, algorithm analysis and automated test cases. The information screen includes the project explanation, authors, data structures and algorithms.

Java replaces the report's proposed Python language at the user's request. This is a single-process educational simulator, with bounded memory and small configurable windows. External live platforms, databases, CSV/JSON ingestion, distributed processing and production-scale throughput benchmarking are extensions, not implemented features. No dataset is needed. State is in memory and resets when the application closes.

## Verification

```sh
java -ea DetectorTest
```

Tests cover constant baselines, warm-up, spikes, finite scores, expired entities and alerts, invalid input without mutation, bounded state, and 2,000 deterministic randomized steps against a full-scan statistics and sorted-ranking oracle. To render a deterministic dashboard preview without opening a window:

```sh
java -Djava.awt.headless=true StreamWatch --preview preview.png
```

## Academic credits

Group 13: **Pavankumar Thatipamula (61), Yash Satam (54), Neev Patel (40)**, as listed in the supplied report. Guide: **Mrs Bhagyashree Patil**. Artificial Intelligence & Data Science, Shah and Anchor Kutchhi Engineering College. Semester III, ADCOR2PC202, academic year 2026–27.

Background references from the report: Cormen et al., *Introduction to Algorithms*; Reema Thareja, *Data Structures Using C*; Leskovec, Rajaraman and Ullman, *Mining of Massive Datasets*. This implementation uses standard Java collections and Swing.
