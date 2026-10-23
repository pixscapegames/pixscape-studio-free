package games.pixscape.studio.ui.preview;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Align;
import games.pixscape.runtime.render.batch.performance.RenderStats;

public final class RenderStatsOverlay {
    private static final long UI_REFRESH_NS = 250_000_000L;
    private static final Color ACCENT = new Color(.65f, .9f, 1f, 1f);
    private static final String HELP_LINE = "F9: stats  |  F10: details  |  F11: benchmark  |  +/-: zoom  |  Arrows: pan";
    private static final float PAIR_GAP = 12, COLUMN_GAP = 20, ROW_GAP = 9;
    private static final String[] LABELS = {"FPS", "Avg (5 s)", "Frame avg",
            "P95 (600 frames)", "P99 (600 frames)", "Max (600 frames)", "Quads", "Draws", "Flushes",
            "Geometry:", "GPU state:", "Region cache:", "Frame queue:", "Tiled chunks:", "Tiled refs:", "Build:", "Box2D:", "Passes:"};
    private final Stage uiStage;
    private final RenderStats stats;
    private final PreviewFrameCadence cadence;
    private final BitmapFont font = new BitmapFont();
    private final Texture background;
    private final BitmapFontCache[] lines = new BitmapFontCache[LABELS.length];
    private final StringBuilder[] text = new StringBuilder[LABELS.length];
    private final BitmapFontCache[] names = new BitmapFontCache[LABELS.length];
    private final StringBuilder averageLabel = new StringBuilder(32);
    private final GlyphLayout metrics = new GlyphLayout();
    private final float[] labelWidths = new float[3];
    private final float valueWidth, sectionWidth;
    private final BitmapFontCache help = new BitmapFontCache(font);
    private boolean enabled, detailed, dirty = true;
    private long lastUiRefreshNs;
    private float lastWidth = -1, lastHeight = -1, panelBottom, panelWidth, helpHeight, helpWidth;
    private double p95Ms, p99Ms, maxMs, boxStepMs;
    private int boxSubsteps, boxBodies, boxContacts, boxJoints;

    RenderStatsOverlay(Stage uiStage, RenderStats stats, PreviewFrameCadence cadence) {
        this.uiStage = uiStage;
        this.stats = stats;
        this.cadence = cadence;
        font.setUseIntegerPositions(true);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = new BitmapFontCache(font);
            names[i] = new BitmapFontCache(font);
            text[i] = new StringBuilder(256);
        }
        // Reserve complete count and duration fields once, independently of live values.
        valueWidth = measure("-8,888,888,888 ms");
        for (int i = 0; i < 9; i++) labelWidths[i % 3] = Math.max(labelWidths[i % 3], measure(LABELS[i]));
        labelWidths[1] = Math.max(labelWidths[1], measure("Avg (partial 8.88 s)"));
        float widestSection = 0;
        for (int i = 9; i < LABELS.length; i++) widestSection = Math.max(widestSection, measure(LABELS[i]));
        sectionWidth = widestSection;
        Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixel.setColor(Color.WHITE);
        pixel.fill();
        background = new Texture(pixel);
        pixel.dispose();
    }

    public void toggle() { setEnabled(!enabled); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; refresh(); }
    public boolean isEnabled() { return enabled; }
    public boolean isDetailed() { return detailed; }
    public void toggleDetails() {
        detailed = !enabled || !detailed;
        enabled = true;
        refresh();
    }
    void refresh() { dirty = true; }

    public void setFrameTimes(double p95Ms, double p99Ms, double maxMs) {
        this.p95Ms = p95Ms; this.p99Ms = p99Ms; this.maxMs = maxMs;
    }
    public void setBox2dStats(double stepMs, int substeps, int bodies, int contacts, int joints) {
        boxStepMs = stepMs; boxSubsteps = substeps;
        boxBodies = bodies; boxContacts = contacts; boxJoints = joints;
    }

    public void render(long nowNs) {
        float width = uiStage.getViewport().getWorldWidth(), height = uiStage.getViewport().getWorldHeight();
        if (dirty || width != lastWidth || height != lastHeight
                || enabled && nowNs - lastUiRefreshNs >= UI_REFRESH_NS) {
            layoutLines(width, height);
            lastUiRefreshNs = nowNs;
            dirty = false; lastWidth = width; lastHeight = height;
        }
        var batch = uiStage.getBatch();
        batch.setProjectionMatrix(uiStage.getViewport().getCamera().combined);
        batch.begin();
        Color previous = batch.getColor();
        float r = previous.r, g = previous.g, b = previous.b, a = previous.a;
        // Only the background is translucent; cached glyph colors always have alpha 1.
        batch.setColor(0, 0, 0, .55f);
        batch.draw(background, 4, 4, helpWidth, helpHeight + 12);
        if (enabled) batch.draw(background, 4, panelBottom, panelWidth, height - panelBottom - 4);
        batch.setColor(r, g, b, a);
        help.draw(batch);
        if (enabled) for (int i = 0; i < lines.length; i++) {
            names[i].draw(batch); lines[i].draw(batch);
        }
        batch.end();
    }

    private void layoutLines(float width, float height) {
        float usable = Math.max(1, width - 24);
        help.setPosition(0, 0);
        help.setColor(Color.WHITE);
        var helpLayout = help.setText(HELP_LINE, 12, 0, usable, Align.left, true);
        helpHeight = helpLayout.height; helpWidth = Math.min(width - 8, helpLayout.width + 16);
        help.setPosition(0, helpHeight + 10);
        for (int i = 0; i < lines.length; i++) { lines[i].clear(); names[i].clear(); }
        if (!enabled) return;
        for (StringBuilder value : text) value.setLength(0);
        panelWidth = 0;
        appendFps(text[0], cadence.fps());
        averageLabel.setLength(0);
        if (cadence.isFull()) averageLabel.append(LABELS[1]);
        else { averageLabel.append("Avg (partial "); append2(averageLabel, cadence.seconds()); averageLabel.append(" s)"); }
        appendFps(text[1], cadence.averageFps());
        if (cadence.sampleCount() == 0) text[2].append("--"); else append2(text[2], cadence.averageMs());
        text[2].append(" ms");
        append2(text[3], p95Ms); text[3].append(" ms");
        append2(text[4], p99Ms); text[4].append(" ms");
        append2(text[5], maxMs); text[5].append(" ms");
        RenderStatsTextFormatter.appendCount(text[6], stats.frameQueueQuads);
        RenderStatsTextFormatter.appendCount(text[7], stats.drawCalls);
        RenderStatsTextFormatter.appendCount(text[8], stats.flushes);
        text[8].append(stats.forwardLighting?"  [FWD]":"  [LEG]");
        float y = height - 14;
        y = row(0, y, usable, ACCENT);
        y = row(3, y, usable, Color.WHITE);
        y = row(6, y, usable, Color.WHITE);
        if (detailed) {
            RenderStatsTextFormatter.appendGeometryLine(text[9], stats);
            RenderStatsTextFormatter.appendGpuStateLine(text[10], stats);
            RenderStatsTextFormatter.appendRegionCacheLine(text[11], stats);
            RenderStatsTextFormatter.appendFrameQueueLine(text[12], stats);
            RenderStatsTextFormatter.appendTiledChunksLine(text[13], stats);
            RenderStatsTextFormatter.appendTiledRefsLine(text[14], stats);
            RenderStatsTextFormatter.appendBuildLine(text[15], stats);
            RenderStatsTextFormatter.appendDrawStagesLine(text[17], stats);
            text[16].append("Box2D: step="); append2(text[16], boxStepMs);
            text[16].append("ms sub=").append(boxSubsteps).append("  bodies:").append(boxBodies)
                    .append("  contacts:").append(boxContacts).append("  joints:").append(boxJoints);
            y -= 6;
            for (int i = 9; i < lines.length; i++) {
                // Keep the existing data formatter; only separate its section prefix for layout.
                text[i].delete(0, LABELS[i].length() + 1);
                float dataWidth = usable - sectionWidth - PAIR_GAP;
                if (dataWidth >= measure("submitted=0")) {
                    float nameHeight = put(names[i], LABELS[i], 12, y, sectionWidth, Align.left, Color.LIGHT_GRAY);
                    float dataHeight = put(lines[i], text[i], 12 + sectionWidth + PAIR_GAP, y, dataWidth, Align.left, Color.LIGHT_GRAY);
                    y -= Math.max(nameHeight, dataHeight) + 7;
                } else {
                    y -= put(names[i], LABELS[i], 12, y, usable, Align.left, Color.LIGHT_GRAY) + 7;
                    y -= put(lines[i], text[i], 12, y, usable, Align.left, Color.LIGHT_GRAY) + 7;
                }
            }
        }
        panelBottom = Math.max(helpHeight + 24, y - 2);
        // Short windows keep the summary and help; all details return on a taller resize.
        for (int i = 9; i < lines.length; i++) {
            if (lines[i].getLayouts().size > 0 && lines[i].getY() - lines[i].getLayouts().first().height < panelBottom) {
                lines[i].clear(); names[i].clear();
            }
        }
    }

    private float row(int first, float y, float usable, Color color) {
        float x = 0, rowHeight = 0;
        for (int i = first; i < first + 3; i++) {
            int column = i % 3;
            float preferredWidth = labelWidths[column] + PAIR_GAP + valueWidth;
            float cellWidth = Math.min(preferredWidth, usable);
            if (x > 0 && x + cellWidth > usable) { y -= rowHeight + ROW_GAP; x = 0; rowHeight = 0; }
            CharSequence label = i == 1 ? averageLabel : LABELS[i];
            float cellHeight;
            if (cellWidth >= preferredWidth) {
                float nameHeight = put(names[i], label, x + 12, y, labelWidths[column], Align.left, color);
                float valueHeight = put(lines[i], text[i], x + 12 + labelWidths[column] + PAIR_GAP,
                        y, valueWidth, Align.right, color);
                cellHeight = Math.max(nameHeight, valueHeight);
            } else {
                float nameHeight = put(names[i], label, x + 12, y, cellWidth, Align.left, color);
                cellHeight = nameHeight + 7 + put(lines[i], text[i], x + 12, y - nameHeight - 7,
                        cellWidth, Align.right, color);
            }
            rowHeight = Math.max(rowHeight, cellHeight);
            panelWidth = Math.max(panelWidth, x + cellWidth + 16);
            x += cellWidth + COLUMN_GAP;
        }
        return y - rowHeight - ROW_GAP;
    }
    private float put(BitmapFontCache cache, CharSequence value, float x, float y, float width, int align, Color color) {
        cache.setPosition(0, 0); cache.setColor(color);
        var layout = cache.setText(value, 0, 0, width, align, true);
        cache.setPosition(x, y);
        panelWidth = Math.max(panelWidth, x + layout.width + 4);
        return layout.height;
    }
    private float measure(CharSequence value) {
        metrics.setText(font, value);
        return (float)Math.ceil(metrics.width);
    }
    private void appendFps(StringBuilder out, double value) {
        if (cadence.sampleCount() == 0) out.append("--");
        else RenderStatsTextFormatter.appendCount(out, (int)Math.round(value));
    }
    private static void append2(StringBuilder out, double value) {
        long scaled = Math.round(value * 100);
        out.append(scaled / 100).append('.');
        long fraction = Math.abs(scaled % 100);
        if (fraction < 10) out.append('0');
        out.append(fraction);
    }
    public void dispose() { background.dispose(); font.dispose(); }
}
