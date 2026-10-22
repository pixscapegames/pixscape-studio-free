package games.pixscape.studio.ui.preview;

/** Preview-local start-to-start cadence, independent of simulation delta and Studio FPS. */
final class PreviewFrameCadence {
    private static final long WINDOW_NS = 5_000_000_000L;
    private static final long SHORT_NS = 1_000_000_000L;
    // 256 KiB; even above 6,500 FPS the displayed partial duration remains truthful.
    private final long[] intervals;
    private int next, count, shortCount;
    private long durationNs, shortDurationNs, lastNs;
    private boolean started;

    PreviewFrameCadence() { this(32768); }

    PreviewFrameCadence(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        intervals = new long[capacity];
    }

    void onFrameStart(long nowNs) {
        if (started) {
            long elapsed = nowNs - lastNs;
            // Match the existing Preview pause/debugger convention, but discard stale history too.
            if (elapsed <= 0 || elapsed >= SHORT_NS) reset();
            else add(elapsed);
        }
        lastNs = nowNs;
        started = true;
    }

    private void add(long elapsed) {
        if (count == intervals.length) {
            long oldest = intervals[next];
            durationNs -= oldest;
            if (shortCount == count) { shortDurationNs -= oldest; shortCount--; }
            count--;
        }
        intervals[next] = elapsed;
        next = (next + 1) % intervals.length;
        count++;
        shortCount++;
        durationNs += elapsed;
        shortDurationNs += elapsed;
        // Whole measured frame intervals: the boundary frame may straddle the target window.
        while (count > 1 && durationNs - oldest(count) >= WINDOW_NS) {
            durationNs -= oldest(count);
            count--;
        }
        while (shortCount > 1 && shortDurationNs - oldest(shortCount) >= SHORT_NS) {
            shortDurationNs -= oldest(shortCount);
            shortCount--;
        }
    }

    private long oldest(int size) { return intervals[(next - size + intervals.length) % intervals.length]; }
    int sampleCount() { return count; }
    double seconds() { return durationNs / 1_000_000_000.0; }
    boolean isFull() { return durationNs >= WINDOW_NS; }
    double fps() { return shortDurationNs == 0 ? 0 : shortCount * 1_000_000_000.0 / shortDurationNs; }
    double averageFps() { return durationNs == 0 ? 0 : count * 1_000_000_000.0 / durationNs; }
    double averageMs() { return count == 0 ? 0 : durationNs / (count * 1_000_000.0); }

    void reset() {
        next = count = shortCount = 0;
        durationNs = shortDurationNs = lastNs = 0;
        started = false;
    }
}
