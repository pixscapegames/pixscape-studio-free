package games.pixscape.studio.ui.main;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.studio.service.CoordSpaces;
import space.earlygrey.shapedrawer.ShapeDrawer;

/**
 * Graduated ruler (TOP or LEFT) synchronized with the world camera.
 * - Draws in screen pixels (Stage UI) -> stable thickness while zooming.
 * - Aligns ticks to the grid (gridCellWorld).
 */
public class RulerActor extends VisTable {

    public enum Orientation {TOP, LEFT}

    private final Orientation orientation;
    private final OrthographicCamera worldCam;
    private final CoordSpaces coordSpaces;
    private final ShapeDrawer drawer;
    private final BitmapFont font;

    private final Color bg = new Color(0.12f, 0.16f, 0.12f, 1f);
    private Color tickMinorC = new Color(0.55f, 0.55f, 0.55f, 1f);
    private Color tickMajorC = new Color(0.82f, 0.82f, 0.82f, 1f);
    private Color textColor = new Color(0.88f, 0.88f, 0.88f, 1f);

    private float thicknessPx = 20f;   // height for TOP, width for LEFT
    private float tickMinorPx = 6f;
    private float tickMajorPx = 11f;
    private float linePx = 1f;
    private float targetMajorPx = 100f;  // target spacing between major ticks

    private int decimals = 0;

    private int minorsPerMajor = 0;  // 5 minor ticks per major tick

    private final GlyphLayout layout = new GlyphLayout();
    private final Vector2 tmpScreen = new Vector2();
    private final Vector2 tmpScreen2 = new Vector2();
    private final Vector2 tmpStage = new Vector2();
    private final Vector2 tmpStage2 = new Vector2();
    private final Vector2 tmpWorld = new Vector2();

    public static final float TOP_HEIGHT = 25;
    public static final float LEFT_WIDTH = 40;


    public RulerActor(Orientation orientation,
                      OrthographicCamera worldCam,
                      CoordSpaces coordSpaces,
                      ShapeDrawer drawer,
                      BitmapFont font) {
        this.orientation = orientation;
        this.worldCam = worldCam;
        this.coordSpaces = coordSpaces;
        this.drawer = drawer;
        this.font = font;
        this.setColor(Color.YELLOW);
        setTouchable(Touchable.disabled);
    }

    public RulerActor setThicknessPx(float px) {
        this.thicknessPx = px;
        setSizeForOrientation();
        return this;
    }

    public RulerActor setTickMinorPx(float px) {
        this.tickMinorPx = px;
        return this;
    }

    public RulerActor setTickMajorPx(float px) {
        this.tickMajorPx = px;
        return this;
    }

    public RulerActor setLinePx(float px) {
        this.linePx = px;
        return this;
    }

    public RulerActor setTargetMajorPx(float px) {
        this.targetMajorPx = px;
        return this;
    }

    public RulerActor setDecimals(int d) {
        this.decimals = Math.max(0, d);
        return this;
    }

    public RulerActor setMinorsPerMajor(int n) {
        this.minorsPerMajor = Math.max(1, n);
        return this;
    }

    public RulerActor setColors(Color bg, Color minor, Color major, Color text) {
        //if (bg != null) this.bg = bg;
        if (minor != null) this.tickMinorC = minor;
        if (major != null) this.tickMajorC = major;
        if (text != null) this.textColor = text;
        return this;
    }

    private void setSizeForOrientation() {
        if (orientation == Orientation.TOP) setHeight(thicknessPx);
        else setWidth(thicknessPx);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        // fond
        drawer.setColor(bg);
        drawer.filledRectangle(getX(), getY(), getWidth(), getHeight());

        // px ↔ monde (Ortho): pxPerWorld = 1/zoom
        float pxPerWorld = 1f / worldCam.zoom;

        // desired major step in WORLD
        float desiredMajorWorld = targetMajorPx / pxPerWorld;

        // nice major step, optionally a multiple of the grid
        float stepMajorWorld = (GridActor.LENGTH_CELL > 0f)
                ? niceMultipleOfCell(desiredMajorWorld, GridActor.LENGTH_CELL)
                : niceStep(desiredMajorWorld);

        // derived minor step
        float stepMinorWorld = stepMajorWorld / minorsPerMajor;
        if (GridActor.LENGTH_CELL > 0f) {
            // round the minor step to a cell multiple
            float k = Math.max(1f, Math.round(stepMinorWorld / GridActor.LENGTH_CELL));
            stepMinorWorld = k * GridActor.LENGTH_CELL;
        }

        if (orientation == Orientation.TOP) drawTop(batch, stepMajorWorld, stepMinorWorld);
        else drawLeft(batch, stepMajorWorld, stepMinorWorld);
    }

    /* ================= TOP ================= */
    private void drawTop(Batch batch, float stepMajorWorld, float stepMinorWorld) {
        if (getStage() == null || getWidth() <= 0f || getHeight() <= 0f) {
            return;
        }

        float rulerTop = getY() + getHeight();
        float minX = visibleWorldMinX();
        float maxX = visibleWorldMaxX();
        if (MathUtils.isEqual(minX, maxX)) {
            return;
        }

        int startMinor = MathUtils.floor(minX / stepMinorWorld);
        int endMinor = MathUtils.ceil(maxX / stepMinorWorld);

        for (int i = startMinor; i <= endMinor; i++) {
            float xWorld = i * stepMinorWorld;
            float xScreen = worldToRulerX(xWorld, minX, maxX);
            if (xScreen < getX() || xScreen > getX() + getWidth()) {
                continue;
            }

            boolean isMajor = (Math.abs(i % minorsPerMajor) == 0);
            float tickLen = isMajor ? tickMajorPx : tickMinorPx;

            // graduations
            drawer.setColor(isMajor ? tickMajorC : tickMinorC);
            drawer.line(xScreen, rulerTop - getHeight() + tickLen, xScreen, rulerTop - getHeight(), linePx);

            // labels
            if (isMajor) {
                String txt = formatValue(xWorld);
                layout.setText(font, txt);
                float bx = xScreen + 3;
                float by = rulerTop - getHeight() + layout.height + 3; // baseline below the tick
                if (bx > getX() + getWidth()) {
                    continue;
                }
                font.setColor(textColor);
                font.draw(batch, layout, bx, by);
            }
        }
        drawer.setColor(bg);
        drawer.filledRectangle(0, getY(), LEFT_WIDTH, TOP_HEIGHT);
    }

    /* ================= LEFT ================= */
    private void drawLeft(Batch batch, float stepMajorWorld, float stepMinorWorld) {
        if (getStage() == null || getWidth() <= 0f || getHeight() <= 0f) {
            return;
        }

        float rulerLeft = getX() + getWidth();
        float minY = visibleWorldMinY();
        float maxY = visibleWorldMaxY();
        if (MathUtils.isEqual(minY, maxY)) {
            return;
        }

        int startMinor = MathUtils.floor(minY / stepMinorWorld);
        int endMinor = MathUtils.ceil(maxY / stepMinorWorld);

        for (int j = startMinor; j <= endMinor; j++) {
            float yWorld = j * stepMinorWorld;
            float yScreen = worldToRulerY(yWorld, minY, maxY);
            if (yScreen < getY() || yScreen > getY() + getHeight()) {
                continue;
            }

            boolean isMajor = (Math.abs(j % minorsPerMajor) == 0);
            float tickLen = isMajor ? tickMajorPx : tickMinorPx;

            drawer.setColor(isMajor ? tickMajorC : tickMinorC);
            drawer.line(rulerLeft, yScreen, rulerLeft - tickLen, yScreen, linePx);

            if (isMajor) {
                String txt = formatValue(yWorld);
                layout.setText(font, txt);

                font.setColor(textColor);
                float bx = rulerLeft - layout.width - 3; //      - labelPadPx;
                float by = yScreen + layout.height + 3;
                if (by > getY() + getHeight()) {
                    continue;
                }
                font.draw(batch, layout, bx, by);
            }
        }
    }

    /* ------------- utils ------------- */

    private float worldToRulerX(float xWorld, float minX, float maxX) {
        float t = (xWorld - minX) / (maxX - minX);
        return getX() + t * getWidth();
    }

    private float worldToRulerY(float yWorld, float minY, float maxY) {
        float t = (yWorld - minY) / (maxY - minY);
        return getY() + t * getHeight();
    }

    private float visibleWorldMinX() {
        localToScreen(0f, 0f, tmpScreen);
        coordSpaces.screenToWorld(tmpScreen.x, tmpScreen.y, tmpWorld);
        return tmpWorld.x;
    }

    private float visibleWorldMaxX() {
        localToScreen(getWidth(), 0f, tmpScreen2);
        coordSpaces.screenToWorld(tmpScreen2.x, tmpScreen2.y, tmpWorld);
        return tmpWorld.x;
    }

    private float visibleWorldMinY() {
        localToScreen(0f, 0f, tmpScreen);
        coordSpaces.screenToWorld(tmpScreen.x, tmpScreen.y, tmpWorld);
        return tmpWorld.y;
    }

    private float visibleWorldMaxY() {
        localToScreen(0f, getHeight(), tmpScreen2);
        coordSpaces.screenToWorld(tmpScreen2.x, tmpScreen2.y, tmpWorld);
        return tmpWorld.y;
    }

    private void localToScreen(float localX, float localY, Vector2 out) {
        tmpStage.set(localX, localY);
        localToStageCoordinates(tmpStage);
        tmpStage2.set(tmpStage);
        getStage().stageToScreenCoordinates(tmpStage2);
        out.set(tmpStage2);
    }

    /**
     * 1–2–5 * 10^n proche de x.
     */
    private static float niceStep(float x) {
        if (x <= 0) return 1f;
        float exp = (float) Math.floor(Math.log10(x));
        float f = (float) (x / Math.pow(10, exp));
        float nf;
        if (f < 1.5f) nf = 1f;
        else if (f < 3.5f) nf = 2f;
        else if (f < 7.5f) nf = 5f;
        else {
            nf = 1f;
            exp += 1f;
        }
        return (float) (nf * Math.pow(10, exp));
    }

    /**
     * Multiple "joli" (×1,×2,×5,×10,…) de cellWorld proche de desired.
     */
    private static float niceMultipleOfCell(float desired, float cellWorld) {
        if (cellWorld <= 0f) return desired;
        float ratio = desired / cellWorld;
        float exp = (float) Math.floor(Math.log10(ratio));
        float c1 = (float) Math.pow(10, exp) * cellWorld; // cell × 10^n

        float c2 = c1 * 2f, c5 = c1 * 5f, c10 = c1 * 10f;
        float best = c1, bestDiff = Math.abs(c1 - desired);
        if (Math.abs(c2 - desired) < bestDiff) {
            best = c2;
            bestDiff = Math.abs(c2 - desired);
        }
        if (Math.abs(c5 - desired) < bestDiff) {
            best = c5;
            bestDiff = Math.abs(c5 - desired);
        }
        if (Math.abs(c10 - desired) < bestDiff) {
            best = c10;
        }
        return best;
    }

    private String formatValue(float v) {
        if (decimals <= 0) return Integer.toString(MathUtils.round(v));
        return String.format("%." + decimals + "f", v);
    }
}
