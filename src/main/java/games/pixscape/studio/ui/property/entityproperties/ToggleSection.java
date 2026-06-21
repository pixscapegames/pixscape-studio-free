package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.CollapsibleWidget;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.studio.ui.config.CommonLayout;

public final class ToggleSection extends VisTable {
    private final String title;
    private final VisTextButton button;
    private final CollapsibleWidget panel;

    private boolean applicable = true;

    public ToggleSection(String title, CollapsibleWidget panel) {
        super(true);
        this.title = title;
        this.panel = panel;

        defaults().top().left().growX();

        button = new VisTextButton("");
        button.setColor(CommonLayout.BUTTON_COLOR);
        button.getLabel().setAlignment(com.badlogic.gdx.utils.Align.left);

        panel.setCollapsed(false); // not collapsed by default

        add(button).growX().row();
        add(panel).growX().row();

        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!applicable) return;
                panel.setCollapsed(!panel.isCollapsed());
                syncButtonText();
                event.handle();
            }
        });

        syncButtonText();
    }

    public void setApplicable(boolean show) {
        this.applicable = show;
        setVisible(show);
        setTouchable(show ? Touchable.enabled : Touchable.disabled);

        panel.setCollapsed(!show);
        syncButtonText();
        invalidateHierarchy();
    }

    public void syncButtonText() {
        button.setText((panel.isCollapsed() ? "▸ " : "▾ ") + title);
    }

    // >>> IMPORTANT: Table must reserve no height when not applicable
    @Override
    public float getPrefHeight() {
        return applicable ? super.getPrefHeight() : 0f;
    }

    @Override
    public float getMinHeight() {
        return applicable ? super.getMinHeight() : 0f;
    }

    @Override
    public float getMaxHeight() {
        return applicable ? super.getMaxHeight() : 0f;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (!applicable) return;
        super.draw(batch, parentAlpha);
    }

    @Override
    public void act(float delta) {
        if (!applicable) return;
        super.act(delta);
    }
}
