package games.pixscape.studio.service.hud;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import games.pixscape.runtime.hud.HudMaterializer;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.MaterializedHud;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudValidationResult;
import games.pixscape.studio.asset.AssetMetaDatabase;

/** One isolated, native Scene2D HUD instance used only by Studio's interactive test mode. */
final class HudInteractiveTestSession implements Disposable {
    private final ScreenViewport viewport = new ScreenViewport();
    private final Stage stage;
    private HudAuthoringResources resources;
    private MaterializedHud materialized;
    private final Rectangle clipBounds = new Rectangle();
    private int x = Integer.MIN_VALUE;
    private int y;
    private int width;
    private int height;
    private boolean disposed;

    private HudInteractiveTestSession(Batch batch,
                                      HudAuthoringResources resources,
                                      MaterializedHud materialized) {
        this.resources = resources;
        this.materialized = materialized;
        stage = new Stage(viewport, batch); // The Studio Batch is borrowed by this Stage.
        // A root Window must be a direct Stage child for native keepWithinStage drag bounds.
        try {
            stage.addActor(materialized.root());
        } catch (RuntimeException failure) {
            stage.dispose();
            throw failure;
        }
        stage.setDebugAll(false);
    }

    static HudInteractiveTestSession create(FileHandle projectDir, AssetMetaDatabase database,
                                            Batch batch, HudScreenAsset asset,
                                            HudDocumentV1 document) {
        if (projectDir == null || batch == null || asset == null || document == null) {
            throw new HudEditRejectedException("HUD test mode requires an active, testable HUD document.");
        }
        HudValidationResult structural = new HudDocumentValidator().validate(document);
        requireValid(structural);
        HudAuthoringResources candidateResources = null;
        MaterializedHud candidate = null;
        try {
            candidateResources = HudAuthoringResources.prepare(
                    structural.validatedDocument(), projectDir, database, asset.skinId);
            HudValidationResult resourceValidation = new HudDocumentValidator().validate(
                    document, candidateResources);
            requireValid(resourceValidation);
            candidate = new HudMaterializer().materialize(
                    resourceValidation.validatedDocument(), candidateResources);
            return new HudInteractiveTestSession(batch, candidateResources, candidate);
        } catch (RuntimeException failure) {
            disposeCandidate(candidate, failure);
            disposeCandidate(candidateResources, failure);
            throw failure;
        }
    }

    void configure(int x, int y, int width, int height) {
        if (disposed || width <= 0 || height <= 0) return;
        if (this.x == x && this.y == y && this.width == width && this.height == height) return;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        viewport.update(width, height, true);
        viewport.setScreenPosition(x, y);
        viewport.apply(false);
        Actor root = materialized.root();
        root.setBounds(0f, 0f, viewport.getWorldWidth(), viewport.getWorldHeight());
        if (root instanceof Layout layout) {
            layout.invalidateHierarchy();
            layout.validate();
        }
    }

    void act(float delta) { if (!disposed) stage.act(delta); }

    void draw() {
        if (disposed || x == Integer.MIN_VALUE) return;
        viewport.apply(false);
        clipBounds.set(viewport.getScreenX(), viewport.getScreenY(),
                viewport.getScreenWidth(), viewport.getScreenHeight());
        if (!ScissorStack.pushScissors(clipBounds)) return;
        try {
            stage.draw();
        } finally {
            ScissorStack.popScissors();
        }
    }

    Stage stage() { return disposed ? null : stage; }
    Actor actor(String nodeId) {
        return disposed || materialized == null ? null : materialized.actor(nodeId);
    }

    boolean containsScreenPoint(int screenX, int screenY, int logicalWindowHeight) {
        if (disposed || x == Integer.MIN_VALUE) return false;
        int bottomY = logicalWindowHeight - screenY;
        return screenX >= viewport.getScreenX()
                && screenX < viewport.getScreenX() + viewport.getScreenWidth()
                && bottomY >= viewport.getScreenY()
                && bottomY < viewport.getScreenY() + viewport.getScreenHeight();
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        stage.cancelTouchFocus();
        stage.unfocusAll();
        stage.getRoot().clearChildren();
        stage.dispose();
        try {
            if (materialized != null) materialized.dispose();
        } finally {
            materialized = null;
            if (resources != null) resources.dispose();
            resources = null;
        }
    }

    private static void requireValid(HudValidationResult result) {
        if (result.isValid()) return;
        StringBuilder message = new StringBuilder("HUD test document validation failed:");
        result.issues().forEach(issue -> message.append("\n").append(issue.code())
                .append(" at ").append(issue.path()).append(": ").append(issue.message()));
        throw new HudEditRejectedException(message.toString());
    }

    private static void disposeCandidate(Disposable candidate, RuntimeException failure) {
        if (candidate == null) return;
        try { candidate.dispose(); }
        catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
    }
}
