package games.pixscape.html.client;

import java.util.Arrays;

/**
 * Computes avg/p95/p99/max over a sliding window of N frames.
 * Measures start-to-start frame pacing, in nanoseconds.
 */
public final class FrameTimePercentiles {

    private final long[] ring;
    private final long[] scratch;
    private int size = 0;
    private int pos = 0;

    private long lastFrameStartNs = 0L;
    private long lastComputeNs = 0L;

    public double avgMs = 0.0;
    public double p95Ms = 0.0;
    public double p99Ms = 0.0;
    public double maxMs = 0.0;
    public double lastMs = 0.0;

    public FrameTimePercentiles(int windowSize) {
        if (windowSize < 64) throw new IllegalArgumentException("windowSize too small: " + windowSize);
        this.ring = new long[windowSize];
        this.scratch = new long[windowSize];
    }

    /**
     * Call at the very beginning of render().
     */
    public void onFrameStart(long nowNs) {
        if (lastFrameStartNs != 0L) {
            long dt = nowNs - lastFrameStartNs;
            lastMs = dt / 1_000_000.0;

            // Filter out unrealistic values from pauses, debugging, or alt-tab.
            if (dt > 0L && dt < 1_000_000_000L) { // < 1 seconde
                ring[pos] = dt;
                pos = (pos + 1) % ring.length;
                if (size < ring.length) size++;
            }
        }
        lastFrameStartNs = nowNs;
    }

    /**
     * Recomputes avg/p95/p99/max if the interval has elapsed.
     *
     * @return true if recompute was performed.
     */
    public boolean computeIfDue(long nowNs, long intervalNs) {
        if (size == 0) return false;
        if (nowNs - lastComputeNs < intervalNs) return false;

        lastComputeNs = nowNs;

        // Copy for sorting (no allocation)
        System.arraycopy(ring, 0, scratch, 0, size);
        Arrays.sort(scratch, 0, size);

        long sum = 0L;
        long max = scratch[size - 1];
        for (int i = 0; i < size; i++) sum += scratch[i];

        avgMs = (sum / (double) size) / 1_000_000.0;
        maxMs = max / 1_000_000.0;
        p95Ms = percentileMs(0.95);
        p99Ms = percentileMs(0.99);

        return true;
    }

    private double percentileMs(double p) {
        // nearest-rank: ceil(p*n)-1
        int idx = (int) Math.ceil(p * size) - 1;
        if (idx < 0) idx = 0;
        if (idx >= size) idx = size - 1;
        return scratch[idx] / 1_000_000.0;
    }

    public void reset() {
        size = 0;
        pos = 0;
        lastFrameStartNs = 0L;
        lastComputeNs = 0L;
        avgMs = p95Ms = p99Ms = maxMs = 0.0;
    }
}
