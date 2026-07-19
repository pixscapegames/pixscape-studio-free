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
    private final StringBuilder sb1 = new StringBuilder(256);
    private final StringBuilder sb2 = new StringBuilder(256);
    private final StringBuilder sb3 = new StringBuilder(256);
    private final StringBuilder sb4 = new StringBuilder(192);
    private final StringBuilder sb5 = new StringBuilder(192);
    private final StringBuilder sb6 = new StringBuilder(192);
    private final StringBuilder sb7 = new StringBuilder(192);
    private final StringBuilder sb8 = new StringBuilder(192);
    private final StringBuilder sb9 = new StringBuilder(192);
    private final StringBuilder sb10 = new StringBuilder(192);
    private String line1 = "";
    private String line2 = "";
    private String line3 = "";
    private String line4 = "";
    private String line5 = "";
    private String line6 = "";
    private String line7 = "";
    private String line8 = "";
    private String line9 = "";
    private String line10 = "";

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
            font.draw(uiStage.getBatch(), line4, x, y - 54f);
            font.draw(uiStage.getBatch(), line5, x, y - 72f);
            font.draw(uiStage.getBatch(), line6, x, y - 90f);
            font.draw(uiStage.getBatch(), line7, x, y - 108f);
            font.draw(uiStage.getBatch(), line8, x, y - 126f);
            font.draw(uiStage.getBatch(), line9, x, y - 144f);
            font.draw(uiStage.getBatch(), line10, x, y - 162f);
        }

        uiStage.getBatch().end();
    }

    private void rebuildLines() {
        sb1.setLength(0);
        sb1.append("fps:").append(Gdx.graphics.getFramesPerSecond()).append(" | ");
        RenderStatsTextFormatter.appendGeometryLine(sb1, stats);
        line1 = sb1.toString();

        sb2.setLength(0);
        RenderStatsTextFormatter.appendGpuDrawLine(sb2, stats);
        line2 = sb2.toString();

        sb3.setLength(0);
        RenderStatsTextFormatter.appendGpuStateLine(sb3, stats);
        line3 = sb3.toString();

        sb4.setLength(0);
        RenderStatsTextFormatter.appendRegionCacheLine(sb4, stats);
        line4 = sb4.toString();

        sb5.setLength(0);
        RenderStatsTextFormatter.appendFrameQueueLine(sb5, stats);
        line5 = sb5.toString();

        sb6.setLength(0);
        RenderStatsTextFormatter.appendTiledChunksLine(sb6, stats);
        line6 = sb6.toString();

        sb7.setLength(0);
        RenderStatsTextFormatter.appendTiledRefsLine(sb7, stats);
        line7 = sb7.toString();

        sb8.setLength(0);
        RenderStatsTextFormatter.appendBuildLine(sb8, stats);
        line8 = sb8.toString();

        sb9.setLength(0);
        sb9.append("Frame ms: avg=");
        append2(sb9, avgMs);
        sb9.append(" p95=");
        append2(sb9, p95Ms);
        sb9.append(" p99=");
        append2(sb9, p99Ms);
        sb9.append(" max=");
        append2(sb9, maxMs);
        line9 = sb9.toString();

        sb10.setLength(0);
        sb10.append("Box2D: step=");
        append2(sb10, boxStepMs);
        sb10.append("ms sub=").append(boxSubsteps)
                .append("  bodies:").append(boxBodies)
                .append("  contacts:").append(boxContacts)
                .append("  joints:").append(boxJoints);
        line10 = sb10.toString();
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
