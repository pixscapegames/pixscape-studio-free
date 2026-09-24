package games.pixscape.studio.service.hud;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.studio.helper.StudioDrawContext;
import games.pixscape.studio.ui.main.GridActor;
import space.earlygrey.shapedrawer.ShapeDrawer;

import java.util.List;

/** Persistent Studio HUD rendering context. The supplied Studio batch is borrowed. */
public final class HudAuthoringSession implements Disposable {
    private final Viewport viewport;
    private final Stage stage;
    private final Group host = new Group();
    private final HudSelectionOverlay selectionOverlay = new HudSelectionOverlay();
    private final Vector2 overlayCoordinates = new Vector2();
    private boolean disposed;
    private boolean viewportInitialized;

    public HudAuthoringSession(Batch batch) {
        this(batch, null);
    }

    public HudAuthoringSession(Batch batch, ShapeDrawer drawer) {
        if (batch == null) throw new IllegalArgumentException("Studio batch is required.");
        viewport = new ScreenViewport();
        stage = new Stage(viewport, batch);
        if (drawer != null && batch instanceof SpriteBatch spriteBatch) {
            GridActor grid = new GridActor(new StudioDrawContext(spriteBatch, drawer,
                    (OrthographicCamera) viewport.getCamera(), viewport));
            grid.setFreeMode();
            stage.addActor(grid);
        }
        stage.addActor(host);
        stage.addActor(selectionOverlay);
    }
    public void configure(HudScreenAsset asset, int x, int y, int width, int height) {
        if (asset == null || width <= 0 || height <= 0) throw new IllegalArgumentException("HUD bounds are required.");
        viewport.update(width, height, false);
        viewport.setScreenPosition(x, y);
        if (!viewportInitialized) {
            viewport.getCamera().position.set(asset.referenceWidth * 0.5f,
                    asset.referenceHeight * 0.5f, 0f);
            viewportInitialized = true;
        }
        viewport.apply(false);
        host.setBounds(0f, 0f, asset.referenceWidth, asset.referenceHeight);
        selectionOverlay.setBounds(0f, 0f, asset.referenceWidth, asset.referenceHeight);
    }
    public void prepare(Actor root) {
        root.setBounds(0f, 0f, host.getWidth(), host.getHeight());
        layout(root);
    }
    public void replace(Actor previous, Actor next) {
        stage.unfocusAll();
        if (previous != null) previous.remove();
        try {
            host.addActor(next);
            applyDebugState();
        }
        catch (RuntimeException failure) {
            next.remove();
            if (previous != null) host.addActor(previous);
            throw failure;
        }
    }
    /** Applies editor-only Scene2D debug state to this persistent HUD Stage. */
    public void setShowLayoutBounds(boolean show) {
        stage.setDebugAll(show);
        applyDebugState();
    }
    public boolean isShowingLayoutBounds() { return stage.isDebugAll(); }
    void setSelectionTargets(List<HudSelectionTarget> targets) {
        selectionOverlay.rebuild(targets);
        selectionOverlay.setDebug(false, true);
    }
    HudSelectionTarget selectionTargetAt(float stageX, float stageY) {
        toOverlayCoordinates(stageX, stageY);
        return selectionOverlay.findTargetAt(overlayCoordinates.x, overlayCoordinates.y);
    }
    HudTransformHandle transformHandleAt(float stageX, float stageY) {
        toOverlayCoordinates(stageX, stageY);
        return selectionOverlay.transformHandleAt(overlayCoordinates.x, overlayCoordinates.y);
    }
    boolean containsTransformBounds(float stageX, float stageY) {
        toOverlayCoordinates(stageX, stageY);
        return selectionOverlay.containsTransformBounds(overlayCoordinates.x, overlayCoordinates.y);
    }
    void showTransformGizmo(HudTransformGeometry.Bounds bounds) { selectionOverlay.showTransformGizmo(bounds); }
    void clearTransformGizmo() { selectionOverlay.clearTransformGizmo(); }
    void showDropFeedback(String destinationName, Rectangle bounds) {
        selectionOverlay.showDropFeedback(destinationName, bounds);
    }
    void clearDropFeedback() { selectionOverlay.clearDropFeedback(); }
    void showCellDragGhost(String widgetId, float stageX, float stageY) {
        toOverlayCoordinates(stageX, stageY);
        selectionOverlay.showCellDragGhost(widgetId, overlayCoordinates.x, overlayCoordinates.y);
    }
    void clearCellDragGhost() { selectionOverlay.clearCellDragGhost(); }
    void showCellRange(List<Rectangle> bounds) { selectionOverlay.showCellRange(bounds); }
    void previewTransformBounds(HudTransformGeometry.Bounds bounds) { selectionOverlay.previewSelectedBounds(bounds); }
    void restoreTransformPreview() { selectionOverlay.restoreSelectedBounds(); }
    void previewAuthoredActorBounds(Actor actor, HudTransformGeometry.Bounds overlayBounds) {
        setActorBoundsFromOverlay(actor, overlayBounds);
    }
    void restoreAuthoredActorBounds(Actor actor, HudTransformGeometry.Bounds overlayBounds) {
        setActorBoundsFromOverlay(actor, overlayBounds);
    }
    HudTransformGeometry.Bounds transformBounds() { return selectionOverlay.transformBounds(); }
    boolean isShowingTransformGizmo() { return selectionOverlay.isShowingTransformGizmo(); }
    float hudUnitsForLogicalPixels(float pixels) {
        if (viewport.getScreenWidth() <= 0 || viewport.getScreenHeight() <= 0) return pixels;
        return pixels * Math.max(viewport.getWorldWidth() / viewport.getScreenWidth(),
                viewport.getWorldHeight() / viewport.getScreenHeight());
    }
    boolean setHoveredSelectionTarget(HudSelectionTarget target) {
        return selectionOverlay.setHoveredTarget(target);
    }
    boolean clearHoveredSelectionTarget() { return selectionOverlay.clearHoveredTarget(); }
    HudSelectionTarget hoveredSelectionTarget() { return selectionOverlay.hoveredTarget(); }
    HudSelectionOverlay.VisualState selectionVisualState(HudSelectionTarget target) {
        return selectionOverlay.visualState(target);
    }
    List<HudSelectionTarget> selectionTargets() { return selectionOverlay.targets(); }
    Actor selectionOverlayActor() { return selectionOverlay; }
    public void clear() {
        stage.unfocusAll();
        host.clearChildren();
        selectionOverlay.clearTargets();
    }
    public void act(float delta) { stage.act(delta); }
    /**
     * Draw authored Scene2D content and its optional layout-debug pass before the editor-only overlay.
     * Stage draws debug geometry after its normal actor pass, so keeping the overlay visible during that
     * call would allow debugAll() to overpaint the gizmo and selection outlines.
     */
    public void draw() {
        boolean overlayVisible = selectionOverlay.isVisible();
        selectionOverlay.setDebug(false, true);
        selectionOverlay.setVisible(false);
        try {
            viewport.apply(false);
            stage.draw();
        } finally {
            selectionOverlay.setVisible(overlayVisible);
        }
        // Stage.drawDebug() invokes debugAll() again, so restore the editor-only exclusion afterwards.
        selectionOverlay.setDebug(false, true);
        if (!overlayVisible) return;

        // Reuse the Stage batch and HUD camera; this is the only editor-overlay draw for the frame.
        viewport.apply(false);
        Batch batch = stage.getBatch();
        batch.setProjectionMatrix(stage.getCamera().combined);
        batch.begin();
        selectionOverlay.draw(batch, 1f);
        batch.end();
    }
    public Viewport viewport() { return viewport; }
    public float referenceWidth() { return host.getWidth(); }
    public float referenceHeight() { return host.getHeight(); }
    public Stage stage() { return stage; }
    Group authoredHost() { return host; }
    Group selectionOverlayHost() { return selectionOverlay; }
    private void toOverlayCoordinates(float stageX, float stageY) {
        selectionOverlay.stageToLocalCoordinates(overlayCoordinates.set(stageX, stageY));
    }
    private void setActorBoundsFromOverlay(Actor actor, HudTransformGeometry.Bounds overlayBounds) {
        if (actor == null || actor.getParent() == null) return;
        Vector2 lower = selectionOverlay.localToStageCoordinates(
                new Vector2(overlayBounds.x(), overlayBounds.y()));
        Vector2 upper = selectionOverlay.localToStageCoordinates(
                new Vector2(overlayBounds.right(), overlayBounds.top()));
        actor.getParent().stageToLocalCoordinates(lower);
        actor.getParent().stageToLocalCoordinates(upper);
        actor.setBounds(lower.x, lower.y, upper.x - lower.x, upper.y - lower.y);
    }
    private void layout(Actor root) {
        if (root instanceof Layout layout) { layout.invalidateHierarchy(); layout.validate(); }
    }
    private void applyDebugState() {
        if (stage.isDebugAll()) stage.getRoot().debugAll();
        selectionOverlay.setDebug(false, true);
    }
    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        stage.dispose();
    }
}
