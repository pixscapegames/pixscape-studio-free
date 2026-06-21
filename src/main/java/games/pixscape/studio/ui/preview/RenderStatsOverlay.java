package games.pixscape.studio.ui.preview;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Stage;
import games.pixscape.runtime.render.batch.performance.RenderStats;

public final class RenderStatsOverlay {

    private static final long UI_REFRESH_NS = 250_000_000L; // 250ms

    private final Stage uiStage;
    private final RenderStats stats;
    private final BitmapFont font;

    private boolean enabled = false;

    // Frame timings (set from PreviewWindow)
    private double avgMs, p95Ms, p99Ms, maxMs;

    // Box2D (set from PreviewWindow)
    private double boxStepMs;
    private int boxSubsteps;
    private int boxBodies, boxContacts, boxJoints;

    // Text cache (avoids per-frame allocations)
    private final StringBuilder sb1 = new StringBuilder(192);
    private final StringBuilder sb2 = new StringBuilder(192);
    private final StringBuilder sb3 = new StringBuilder(192);
    private String line1 = "";
    private String line2 = "";
    private String line3 = "";

    private long lastUiRefreshNs = 0L;

    private static final String HELP_LINE = "F9: stats  |  +/-: zoom  |  Arrow keys: pan";

    public RenderStatsOverlay(Stage uiStage, RenderStats stats) {
        this.uiStage = uiStage;
        this.stats = stats;
        font = new BitmapFont();
        font.setColor(Color.WHITE);
        font.setUseIntegerPositions(true);
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            // Force an immediate refresh on activation
            lastUiRefreshNs = 0L;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Called from PreviewWindow when percentiles are recomputed.
     */
    public void setFrameTimes(double avgMs, double p95Ms, double p99Ms, double maxMs) {
        this.avgMs = avgMs;
        this.p95Ms = p95Ms;
        this.p99Ms = p99Ms;
        this.maxMs = maxMs;
    }

    public void render(long nowNs) {
        float x = 10f;

        uiStage.getBatch().setProjectionMatrix(uiStage.getViewport().getCamera().combined);
        uiStage.getBatch().begin();

        font.draw(uiStage.getBatch(), HELP_LINE, x, 18f);

        if (enabled) {
            if (nowNs - lastUiRefreshNs >= UI_REFRESH_NS) {
                lastUiRefreshNs = nowNs;
                rebuildLines();
            }

            float y = uiStage.getViewport().getWorldHeight() - 10f;

            font.draw(uiStage.getBatch(), line1, x, y);
            font.draw(uiStage.getBatch(), line2, x, y - 18f);
            font.draw(uiStage.getBatch(), line3, x, y - 36f);
        }

        uiStage.getBatch().end();
    }

    private void rebuildLines() {
        // Ligne 1: rendu
        sb1.setLength(0);
        sb1.append("fps:").append(Gdx.graphics.getFramesPerSecond())
                .append(" | quads:").append(stats.drawnQuads)
                .append(" | draws:").append(stats.drawCalls)
                .append(" | flush:").append(stats.flushes)
                .append(" (cap:").append(stats.flushCapacity)
                .append(", state:").append(stats.flushStateChanges)
                .append(")")
                .append(" | texBinds:").append(stats.textureBinds)
                .append(" | shaderSw:").append(stats.shaderSwitches)
                .append(" | blendSw:").append(stats.blendSwitches);

        line1 = sb1.toString();

        // Ligne 2: frame times
        sb2.setLength(0);
        sb2.append("ms avg:");
        append2(sb2, avgMs);
        sb2.append("  p95:");
        append2(sb2, p95Ms);
        sb2.append("  p99:");
        append2(sb2, p99Ms);
        sb2.append("  max:");
        append2(sb2, maxMs);

        line2 = sb2.toString();

        sb3.setLength(0);
        sb3.append("box2d step:");
        append2(sb3, boxStepMs);
        sb3.append("ms  sub:").append(boxSubsteps)
                .append("  bodies:").append(boxBodies)
                .append("  contacts:").append(boxContacts)
                .append("  joints:").append(boxJoints);

        line3 = sb3.toString();
    }

    /**
     * Fast two-decimal formatting without String.format().
     */
    private static void append2(StringBuilder sb, double v) {
        long scaled = Math.round(v * 100.0);
        long whole = scaled / 100;
        long frac = Math.abs(scaled % 100);
        sb.append(whole).append('.');
        if (frac < 10) sb.append('0');
        sb.append(frac);
    }

    public void setBox2dStats(double stepMs, int substeps, int bodies, int contacts, int joints) {
        this.boxStepMs = stepMs;
        this.boxSubsteps = substeps;
        this.boxBodies = bodies;
        this.boxContacts = contacts;
        this.boxJoints = joints;
    }

    public void dispose() {
        font.dispose();
    }
}