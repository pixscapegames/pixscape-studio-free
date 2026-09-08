package games.pixscape.studio.ui.main;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.Tooltip;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;

public class ToolBar extends VisTable {
    public static final float HEIGHT = 32f;

    private final StudioApplicationAdapter app;
    private final SelectionService selectionService;
    private final SpatialBlockSelectionService spatialBlockSelectionService;

    private final TiledToolBar tiledToolBar;

    private final ImageButton alignLeftButton;
    private final ImageButton alignRightButton;
    private final ImageButton alignTopButton;
    private final ImageButton alignBottomButton;

    private final ImageButton centerHorizontalButton;
    private final ImageButton centerVerticalButton;

    private final ImageButton packHorizontalButton;
    private final ImageButton packVerticalButton;

    public ToolBar(StudioApplicationAdapter app) {
        super(false);
        this.app = app;
        this.selectionService = app.getCanvas().getSelectionService();
        this.spatialBlockSelectionService = app.getCanvas().getSpatialBlockSelectionService();

        setBackground(VisUI.getSkin().getDrawable("default-pane"));
        pad(3f, 6f, 3f, 6f);
        left();

        alignLeftButton = createToolButton("align-left", "Align left", this::onAlignLeft);
        alignRightButton = createToolButton("align-right", "Align right", this::onAlignRight);
        alignTopButton = createToolButton("align-top", "Align top", this::onAlignTop);
        alignBottomButton = createToolButton("align-bottom", "Align bottom", this::onAlignBottom);

        centerVerticalButton = createToolButton("center-vertically", "Center horizontally", this::onCenterVertical);
        centerHorizontalButton = createToolButton("center-horizontally", "Center vertically", this::onCenterHorizontal);

        packHorizontalButton = createToolButton("pack-horizontally", "Pack horizontally", this::onPackHorizontal);
        packVerticalButton = createToolButton("pack-vertically", "Pack vertically", this::onPackVertical);

        add(new VisLabel("Align")).padRight(10f);
        add(alignLeftButton).padRight(7f);
        add(alignRightButton).padRight(7f);
        add(alignTopButton).padRight(7f);
        add(alignBottomButton);
        addSeparator(true).pad(7f);
        add(centerHorizontalButton).padRight(7f);
        add(centerVerticalButton);
        addSeparator(true).pad(7f);
        add(packHorizontalButton).padRight(7f);
        add(packVerticalButton).padRight(35f);
        add(new VisLabel("Tile")).padRight(10f);
        tiledToolBar = new TiledToolBar(app.getCanvas().getTileToolService());
        add(tiledToolBar);

        EventFlow.i().subscribe(EventFlow.EditorModeChanged.class, evt -> {
            updateEditingContextState();
        });
        EventFlow.i().subscribe(EventFlow.SpatialBlockSelectionChanged.class, evt -> {
            updateEditingContextState();
        });

        updateEditingContextState();
    }

    private ImageButton createToolButton(String style, String tooltipText, Runnable action) {
        ImageButton button = new ImageButton(VisUI.getSkin(), style);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        Tooltip tip = new Tooltip.Builder(tooltipText)
                .target(button)
                .build();
        tip.setAppearDelayTime(0f);

        return button;
    }

    private void updateEditingContextState() {
        boolean tiledMapTarget = selectionService.isTiledMapEditingTargetActive();
        tiledToolBar.setDisabled(!tiledMapTarget || isSpatialBlockEditingActive());
        setAlignmentButtonsDisabled(tiledMapTarget);
        invalidateHierarchy();
    }

    private boolean isSpatialBlockEditingActive() {
        return spatialBlockSelectionService != null && spatialBlockSelectionService.isEditingActive();
    }

    public void setAlignmentButtonsDisabled(boolean disabled) {
        alignLeftButton.setDisabled(disabled);
        alignRightButton.setDisabled(disabled);
        alignTopButton.setDisabled(disabled);
        alignBottomButton.setDisabled(disabled);

        centerHorizontalButton.setDisabled(disabled);
        centerVerticalButton.setDisabled(disabled);

        packHorizontalButton.setDisabled(disabled);
        packVerticalButton.setDisabled(disabled);
    }

    private void onAlignLeft() {
        app.getCanvas().getAlignService().alignLeft();
    }

    private void onAlignRight() {
        app.getCanvas().getAlignService().alignRight();
    }

    private void onAlignTop() {
        app.getCanvas().getAlignService().alignTop();
    }

    private void onAlignBottom() {
        app.getCanvas().getAlignService().alignBottom();
    }

    private void onCenterHorizontal() {
        app.getCanvas().getAlignService().centerHorizontal();
    }

    private void onCenterVertical() {
        app.getCanvas().getAlignService().centerVertical();
    }

    private void onPackHorizontal() {
        app.getCanvas().getAlignService().packHorizontal();
    }

    private void onPackVertical() {
        app.getCanvas().getAlignService().packVertical();
    }
}
