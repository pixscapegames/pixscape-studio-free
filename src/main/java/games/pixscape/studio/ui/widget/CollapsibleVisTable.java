package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.kotcrab.vis.ui.widget.CollapsibleWidget;
import com.kotcrab.vis.ui.widget.VisTable;

/**
 * Collapsible properties block, with no gaps in the layout.
 * <p>
 * Uses CollapsibleWidget (VisUI) so getPrefHeight() drops to 0
 * when collapsed, which is key for Table layout.
 */


public final class CollapsibleVisTable extends CollapsibleWidget {

    private final VisTable content;

    public CollapsibleVisTable(boolean spacing, boolean collapsed) {
        super(null, collapsed);

        content = new VisTable(spacing);
        setTable(content);

        // Optional: same behavior as VisUI
        setTouchable(collapsed ? Touchable.disabled : Touchable.enabled);
        setCollapseInterpolation(Interpolation.pow3Out);
        setCollapseDuration(0.15f);
    }

    @Override
    public void layout() {
        super.layout();
        content.setBounds(0, 0, getWidth(), content.getPrefHeight());
    }

    public CollapsibleVisTable(boolean spacing) {
        this(spacing, false);
    }

    public VisTable content() {
        return content;
    }

    /**
     * Petit sugar.
     */
    public void show(boolean show, boolean animate) {
        setCollapsed(!show, animate);
    }

    public void show(boolean show) {
        show(show, false);
    }
}
