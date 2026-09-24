package games.pixscape.studio.service.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.math.Rectangle;
import games.pixscape.studio.helper.ShapeHelper;
import com.kotcrab.vis.ui.widget.VisLabel;

import java.util.List;
import java.util.Objects;

/** One editor-only overlay layer above the authored HUD host. */
final class HudSelectionOverlay extends Group {
    enum VisualState { IDLE, HOVERED, SELECTED }

    private List<HudSelectionTarget> targets = List.of();
    private HudSelectionTarget hoveredTarget;
    private final HudTransformGizmo transformGizmo = new HudTransformGizmo();
    private TargetActor selectedActor;
    private DropFeedbackActor dropFeedback;
    private VisLabel cellDragGhost;
    private final java.util.ArrayList<Actor> rangeActors = new java.util.ArrayList<>();

    HudSelectionOverlay() {
        setName("__pixscape-hud-selection-overlay");
        setTouchable(Touchable.childrenOnly);
    }

    void rebuild(List<HudSelectionTarget> nextTargets) {
        clearChildren();
        targets = List.copyOf(nextTargets);
        hoveredTarget = null;
        selectedActor = null;
        dropFeedback = null;
        cellDragGhost = null;
        rangeActors.clear();
        for (HudSelectionTarget target : targets) {
            TargetActor actor = new TargetActor(target);
            addActor(actor);
            if (target.selected()) selectedActor = actor;
        }
        transformGizmo.clearGizmo();
        addActor(transformGizmo);
    }

    void clearTargets() {
        clearChildren();
        targets = List.of();
        hoveredTarget = null;
        selectedActor = null;
        dropFeedback = null;
        cellDragGhost = null;
        rangeActors.clear();
        transformGizmo.clearGizmo();
        addActor(transformGizmo);
    }

    List<HudSelectionTarget> targets() { return targets; }
    HudSelectionTarget hoveredTarget() { return hoveredTarget; }

    /** Canonical overlay-local target resolution used by both click and hover. */
    HudSelectionTarget findTargetAt(float x, float y) {
        Actor hit = hit(x, y, true);
        return hit instanceof TargetActor targetActor ? targetActor.target : null;
    }

    boolean setHoveredTarget(HudSelectionTarget nextTarget) {
        if (Objects.equals(hoveredTarget, nextTarget)) return false;
        hoveredTarget = nextTarget;
        return true;
    }

    boolean clearHoveredTarget() { return setHoveredTarget(null); }

    VisualState visualState(HudSelectionTarget target) {
        if (target.selected()) return VisualState.SELECTED;
        return target.equals(hoveredTarget) ? VisualState.HOVERED : VisualState.IDLE;
    }

    void showTransformGizmo(HudTransformGeometry.Bounds bounds) { transformGizmo.showFor(bounds); }
    void clearTransformGizmo() { transformGizmo.clearGizmo(); }
    HudTransformHandle transformHandleAt(float x, float y) { return transformGizmo.handleAt(x, y); }
    boolean containsTransformBounds(float x, float y) { return transformGizmo.contains(x, y); }
    HudTransformGeometry.Bounds transformBounds() { return transformGizmo.bounds(); }
    boolean isShowingTransformGizmo() { return transformGizmo.isVisible(); }

    void previewSelectedBounds(HudTransformGeometry.Bounds bounds) {
        if (selectedActor != null) selectedActor.setBounds(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        transformGizmo.showFor(bounds);
    }

    void restoreSelectedBounds() {
        if (selectedActor != null) selectedActor.restoreBounds();
    }

    void showDropFeedback(String destinationName, com.badlogic.gdx.math.Rectangle bounds) {
        if (bounds == null) return;
        if (dropFeedback == null) {
            dropFeedback = new DropFeedbackActor();
            addActor(dropFeedback);
        }
        dropFeedback.configure(destinationName, bounds);
    }

    void clearDropFeedback() {
        if (dropFeedback == null) return;
        dropFeedback.remove();
        dropFeedback = null;
    }

    void showCellDragGhost(String widgetId, float x, float y) {
        if (cellDragGhost == null) {
            cellDragGhost = new VisLabel("");
            cellDragGhost.setTouchable(Touchable.disabled);
            cellDragGhost.setColor(Color.WHITE);
            addActor(cellDragGhost);
        }
        cellDragGhost.setText(widgetId);
        cellDragGhost.pack();
        cellDragGhost.setPosition(x + 12f, y + 12f);
        cellDragGhost.toFront();
    }

    void clearCellDragGhost() {
        if (cellDragGhost == null) return;
        cellDragGhost.remove();
        cellDragGhost = null;
    }

    void showCellRange(List<Rectangle> bounds) {
        for (Actor actor : rangeActors) actor.remove();
        rangeActors.clear();
        for (Rectangle bound : bounds) {
            Actor actor = new Actor() {
                private final Color color = new Color(0.25f, 1f, 0.45f, 0.75f);
                @Override public void draw(Batch batch, float parentAlpha) {
                    float previous = batch.getPackedColor();
                    batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
                    float edge = Math.min(2f, Math.min(getWidth(), getHeight()) * 0.5f);
                    batch.draw(ShapeHelper.whitePixelRegion(), getX(), getY(), getWidth(), edge);
                    batch.draw(ShapeHelper.whitePixelRegion(), getX(), getY() + getHeight() - edge,
                            getWidth(), edge);
                    batch.draw(ShapeHelper.whitePixelRegion(), getX(), getY(), edge, getHeight());
                    batch.draw(ShapeHelper.whitePixelRegion(), getX() + getWidth() - edge, getY(),
                            edge, getHeight());
                    batch.setPackedColor(previous);
                }
            };
            actor.setTouchable(Touchable.disabled);
            actor.setBounds(bound.x, bound.y, bound.width, bound.height);
            rangeActors.add(actor);
            addActor(actor);
        }
    }

    private final class TargetActor extends Actor {
        private static final Color HOVERED = new Color(0.78f, 0.82f, 0.88f, 0.16f);
        private static final Color SELECTED = new Color(0.25f, 1f, 0.45f, 0.28f);
        private final HudSelectionTarget target;

        private TargetActor(HudSelectionTarget target) {
            this.target = target;
            setBounds(target.x(), target.y(), target.width(), target.height());
            setTouchable(target.selected() ? Touchable.disabled : Touchable.enabled);
        }

        private void restoreBounds() {
            setBounds(target.x(), target.y(), target.width(), target.height());
        }

        @Override public void draw(Batch batch, float parentAlpha) {
            VisualState state = visualState(target);
            if (state == VisualState.IDLE) return;
            Color color = state == VisualState.SELECTED ? SELECTED : HOVERED;
            float previous = batch.getPackedColor();
            batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
            batch.draw(ShapeHelper.whitePixelRegion(), getX(), getY(), getWidth(), getHeight());
            batch.setPackedColor(previous);
        }
    }

    /** A transient, non-interactive outline: it is not a selection overlay for the document root. */
    private static final class DropFeedbackActor extends com.badlogic.gdx.scenes.scene2d.Group {
        private static final Color FILL = new Color(0.22f, 0.72f, 1f, 0.09f);
        private static final Color BORDER = new Color(0.22f, 0.72f, 1f, 0.92f);
        private VisLabel label;

        private DropFeedbackActor() {
            setTouchable(Touchable.disabled);
        }

        private void ensureLabel() {
            if (label != null) return;
            label = new VisLabel("");
            label.setTouchable(Touchable.disabled);
            label.setColor(BORDER);
            addActor(label);
        }

        private void configure(String destinationName, com.badlogic.gdx.math.Rectangle bounds) {
            ensureLabel();
            setBounds(bounds.x, bounds.y, bounds.width, bounds.height);
            label.setText("Destination : " + destinationName);
            label.pack();
            label.setPosition(6f, Math.max(2f, getHeight() - label.getHeight() - 4f));
        }

        @Override public void draw(Batch batch, float parentAlpha) {
            float previous = batch.getPackedColor();
            batch.setColor(FILL.r, FILL.g, FILL.b, FILL.a * parentAlpha);
            batch.draw(ShapeHelper.whitePixelRegion(), getX(), getY(), getWidth(), getHeight());
            batch.setColor(BORDER.r, BORDER.g, BORDER.b, BORDER.a * parentAlpha);
            float edge = Math.min(2f, Math.min(getWidth(), getHeight()) * 0.5f);
            batch.draw(ShapeHelper.whitePixelRegion(), getX(), getY(), getWidth(), edge);
            batch.draw(ShapeHelper.whitePixelRegion(), getX(), getY() + getHeight() - edge, getWidth(), edge);
            batch.draw(ShapeHelper.whitePixelRegion(), getX(), getY(), edge, getHeight());
            batch.draw(ShapeHelper.whitePixelRegion(), getX() + getWidth() - edge, getY(), edge, getHeight());
            batch.setPackedColor(previous);
            super.draw(batch, parentAlpha);
        }
    }
}
