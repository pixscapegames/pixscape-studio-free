package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.kotcrab.vis.ui.widget.VisSplitPane;

/**
 * Binary docking split. Only the handle and visible child
 * content participate in hit testing. VisUI still owns rendering and the resize gesture.
 *
 * <p>If the handle cannot fit with positive remaining space, children have zero bounds and
 * this entire split is neither drawn nor hit. The ratio survives until space is available.
 * Any size change cancels this split's gesture: VisUI's private drag origin cannot be rebased.
 * Child minimum sizes are deliberately not enforced; small content is clipped by VisUI.
 * Use a separate instance for each orientation.</p>
 */
public final class DockSplitPane extends VisSplitPane {
    private final boolean vertical;
    private final Rectangle handle = new Rectangle();
    private final Vector2 childPoint = new Vector2();
    private boolean resizeCancelled;

    {
        // Registered after VisUI's listener: observe only handle presses it accepted.
        // Scene2D owns this focus too, so normal release, removal and cancellation end it.
        addListener(new InputListener() {
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return event.getTarget() == DockSplitPane.this && event.isHandled();
            }
            @Override public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                resizeCancelled = event.isTouchFocusCancel();
            }
        });
    }

    /** Returns whether Scene2D actually cancelled an accepted separator gesture. */
    public boolean cancelResize() {
        resizeCancelled = false;
        if (getStage() != null) getStage().cancelTouchFocus(this);
        return resizeCancelled;
    }

    public DockSplitPane(Actor first, Actor second, boolean vertical) {
        super(first, second, vertical);
        this.vertical = vertical;
    }

    public DockSplitPane(Actor first, Actor second, boolean vertical, VisSplitPaneStyle style) {
        super(first, second, vertical, style);
        this.vertical = vertical;
    }

    private boolean hasSpace() {
        return getWidth() > 0 && getHeight() > 0
                && (vertical ? getHeight() > getStyle().handle.getMinHeight()
                             : getWidth() > getStyle().handle.getMinWidth());
    }

    @Override public void layout() {
        if (!hasSpace()) {
            handle.set(0, 0, 0, 0);
            for (Actor child : getChildren()) child.setBounds(0, 0, 0, 0);
            return;
        }
        super.layout();
        // Public snapshots of VisUI's actual geometry, not another layout calculation.
        Rectangle first = getFirstWidgetBounds();
        Rectangle second = getSecondWidgetBounds();
        if (vertical) {
            float bottom = second.y + second.height;
            handle.set(0, bottom, getWidth(), first.y - bottom);
        } else {
            float left = first.x + first.width;
            handle.set(left, 0, second.x - left, getHeight());
        }
    }

    @Override public Actor hit(float x, float y, boolean touchable) {
        if (!isVisible() || touchable && getTouchable() == Touchable.disabled) return null;
        validate();
        if (!hasSpace() || x < 0 || x >= getWidth() || y < 0 || y >= getHeight()) return null;
        if (handle.contains(x, y)) {
            return !touchable || getTouchable() == Touchable.enabled ? this : null;
        }
        // Group.hit permits overflowing descendants. Match the split's visible clipping areas.
        for (int i = getChildren().size - 1; i >= 0; i--) {
            Actor child = getChildren().get(i);
            if (x < child.getX() || x >= child.getRight()
                    || y < child.getY() || y >= child.getTop()) continue;
            child.parentToLocalCoordinates(childPoint.set(x, y));
            Actor target = child.hit(childPoint.x, childPoint.y, touchable);
            if (target != null) return target;
        }
        return null;
    }

    @Override public void draw(Batch batch, float parentAlpha) {
        validate();
        if (hasSpace()) super.draw(batch, parentAlpha);
    }

    @Override protected void sizeChanged() {
        super.sizeChanged();
        // Cancellation delivers native touchUp; removing focus alone would strand draggingPointer.
        if (getStage() != null) getStage().cancelTouchFocus(this);
    }

    @Override public void setVertical(boolean vertical) {
        if (vertical != this.vertical) {
            throw new UnsupportedOperationException("Create a new DockSplitPane to change orientation.");
        }
    }
}
