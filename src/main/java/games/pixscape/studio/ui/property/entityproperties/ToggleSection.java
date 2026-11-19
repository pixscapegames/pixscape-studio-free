package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.kotcrab.vis.ui.VisUI;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.CollapsibleWidget;
import com.kotcrab.vis.ui.widget.Tooltip;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.studio.ui.config.CommonLayout;

public final class ToggleSection extends VisTable {
    private String title;
    private Tooltip titleTooltip;
    private final VisTextButton button;
    private final CollapsibleWidget panel;
    private final VisTable header = new VisTable();
    private Button removeButton;
    private Cell<Button> removeCell;
    private Runnable removal;

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

        header.add(button).growX();
        add(header).growX().row();
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

    /** Optional capability removal; independent from the collapsible title button. */
    public void setRemovalAction(String name, String tooltip, Runnable action) {
        removal = action;
        removeButton = new Button(VisUI.getSkin(), "delete");
        removeButton.setName(name);
        new Tooltip.Builder(tooltip).target(removeButton).build();
        removeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (applicable && removeButton.isVisible() && !removeButton.isDisabled()) {
                    removal.run();
                }
                event.stop();
            }
        });
        removeCell = header.add(removeButton).right();
    }

    public void setRemovalAvailable(boolean visible, boolean enabled) {
        if (removeButton == null) {
            return;
        }
        removeButton.setVisible(visible);
        removeButton.setDisabled(!enabled);
        removeCell.size(visible ? removeButton.getPrefWidth() : 0, visible ? removeButton.getPrefHeight() : 0);
        header.invalidateHierarchy();
    }

    public void expand() {
        panel.setCollapsed(false);
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

    public void setTitle(String title, String tooltipText) {
        this.title = title;
        syncButtonText();
        if (titleTooltip != null) {
            // Cancel any pending tooltip display before detaching its target.
            InputEvent exit = new InputEvent();
            exit.setType(InputEvent.Type.exit);
            exit.setPointer(-1);
            exit.setBubbles(false);
            button.fire(exit);
            titleTooltip.setTarget(null);
            titleTooltip.remove();
            titleTooltip = null;
        }
        if (tooltipText != null) {
            titleTooltip = new Tooltip.Builder(tooltipText).target(button).build();
            titleTooltip.setAppearDelayTime(0f);
        }
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
