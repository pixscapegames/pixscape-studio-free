package games.pixscape.studio.ui.docking;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.studio.ui.config.CommonLayout;

public abstract class DockablePanel extends VisTable {

    public enum DockMode {
        DOCKABLE,   // comportement actuel
        WINDOW_ONLY // always in a window
    }

    protected DockManager dockManager;
    private final VisTable header;
    private final Cell<?> headerCell;
    private DockSlot defaultSlot;
    private final VisLabel titleLabel;

    private DockMode dockMode = DockMode.DOCKABLE;
    private boolean openOnRegister = true;

    private int preferredWindowWidth = 360;
    private int preferredWindowHeight = 520;

    public DockablePanel(String title) {
        Skin skin = VisUI.getSkin();

        this.top().left();

        this.setBackground(skin.getDrawable("default-pane"));
        header = new VisTable(true);
        header.setBackground(skin.getDrawable("panel-header"));
        titleLabel = new VisLabel(title, "title");


        Drawable undockDrawable = skin.getDrawable("select-up");
        Drawable dockDrawable = skin.getDrawable("select-down");

        VisImageButton undockBtn = new VisImageButton(undockDrawable);
        undockBtn.setColor(CommonLayout.BUTTON_COLOR);
        VisImageButton dockBtn = new VisImageButton(dockDrawable);
        dockBtn.setColor(CommonLayout.BUTTON_COLOR);

        undockBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dockManager.undock(DockablePanel.this);
            }
        });

        dockBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dockManager.hide(DockablePanel.this);
            }
        });

        header.add(titleLabel).expandX().center();
        header.add(undockBtn);
        header.add(dockBtn);

        headerCell = add(header).growX();
        row();
    }

    public void attachDockManager(DockManager manager, DockSlot defaultSlot) {
        this.dockManager = manager;
        this.defaultSlot = defaultSlot;
    }

    public DockSlot getDefaultSlot() {
        return defaultSlot;
    }

    public String getTitleText() {
        return titleLabel.getText().toString();
    }

    public void setHeaderVisible(boolean visible) {
        header.setVisible(visible);
        headerCell.height(visible ? header.getPrefHeight() : 0f);
        invalidateHierarchy();
    }

    public void setPreferredWindowSize(int width, int height) {
        this.preferredWindowWidth = width;
        this.preferredWindowHeight = height;
    }

    public int getPreferredWindowWidth() {
        return preferredWindowWidth;
    }

    public int getPreferredWindowHeight() {
        return preferredWindowHeight;
    }

    public void setDockMode(DockMode mode) {
        this.dockMode = mode;
    }

    public DockMode getDockMode() {
        return dockMode;
    }

    public void setOpenOnRegister(boolean open) {
        this.openOnRegister = open;
    }

    public boolean isOpenOnRegister() {
        return openOnRegister;
    }

    public DockManager getDockManager() {
        return dockManager;
    }
}
