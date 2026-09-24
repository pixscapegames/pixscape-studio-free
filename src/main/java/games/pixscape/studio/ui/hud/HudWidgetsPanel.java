package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Scaling;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.Tooltip;
import com.kotcrab.vis.ui.widget.VisImageButton;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.document.OpenEditorDocument;
import games.pixscape.studio.service.hud.HudEditorSession;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.docking.DockablePanel;
import games.pixscape.studio.event.GetScrollListener;
import games.pixscape.studio.event.LoseScroolListener;

import java.util.List;
import java.util.function.Consumer;

/** Persistent active-HUD widget toolbox using the normal dockable-panel lifecycle. */
public final class HudWidgetsPanel extends DockablePanel {
    private static final int MAX_CATEGORY_COLUMNS = 7;
    private static final Color ICON_OVER_TINT = new Color(0.78f, 0.92f, 1f, 1f);
    private static final Color ICON_DOWN_TINT = new Color(0.62f, 0.78f, 0.88f, 1f);
    private static final Color ICON_DISABLED_TINT = new Color(0.45f, 0.45f, 0.45f, 1f);

    private final HudEditorSession session;
    private final EditorDocumentManager documentManager;
    private final Consumer<Runnable> deferUi;
    private final VisTable content = new VisTable(true);
    private final VisScrollPane scrollPane = new VisScrollPane(content);
    private final ImageButton groupButton = authoringButton("widget_group", "Group");
    private final ImageButton tableButton = authoringButton("widget_table", "Table");
    private final ImageButton stackButton = authoringButton("widget_stack", "Stack");
    private final ImageButton containerButton = authoringButton("widget_container", "Container");
    private final ImageButton scrollPaneButton = authoringButton("widget_scrollpane", "Scroll Pane");
    private final ImageButton windowButton = authoringButton("widget_window", "Window");
    private final ImageButton dialogButton = authoringButton("widget_window", "Dialog");
    private final ImageButton labelButton = authoringButton("widget_label", "Label");
    private final ImageButton textraLabelButton = authoringButton("widget_textra", "Textra Label");
    private final ImageButton textButtonButton = authoringButton("widget_textbutton", "Text Button");
    private final ImageButton imageButtonButton = authoringButton("widget_imagebutton", "Image Button");
    private final ImageButton imageTextButtonButton = authoringButton("widget_textimagebutton", "Image Text Button");
    private final ImageButton textFieldButton = authoringButton("widget_textfield", "Text Field");
    private final ImageButton selectBoxButton = authoringButton("widget_selectbox", "Select Box");
    private final ImageButton listButton = authoringButton("widget_list", "List");
    private final ImageButton checkBoxButton = authoringButton("widget_checkbox", "Check Box");
    private final ImageButton sliderButton = authoringButton("widget_slider", "Slider");
    private final ImageButton progressBarButton = authoringButton("widget_progressbar", "Progress Bar");
    private final HorizontalGroup layoutButtons = wrappingButtonGroup(
            groupButton, tableButton, stackButton, containerButton, scrollPaneButton,
            windowButton, dialogButton);
    private final HorizontalGroup textButtons = wrappingButtonGroup(
            labelButton, textraLabelButton, textFieldButton);
    private final HorizontalGroup buttonButtons = wrappingButtonGroup(
            textButtonButton, imageButtonButton, imageTextButtonButton, checkBoxButton);
    private final HorizontalGroup selectionButtons = wrappingButtonGroup(
            selectBoxButton, listButton, sliderButton, progressBarButton);
    private final Container<HorizontalGroup> layoutCategory = categoryBody(layoutButtons);
    private final Container<HorizontalGroup> textCategory = categoryBody(textButtons);
    private final Container<HorizontalGroup> buttonCategory = categoryBody(buttonButtons);
    private final Container<HorizontalGroup> selectionCategory = categoryBody(selectionButtons);
    private boolean refreshPending;
    private boolean refreshing;
    private int projectionCount;

    public HudWidgetsPanel(HudEditorSession session, EditorDocumentManager documentManager) {
        this(session, documentManager, runnable -> {
            if (Gdx.app == null) {
                throw new IllegalStateException("HUD widgets refresh requires the Studio UI loop.");
            }
            Gdx.app.postRunnable(runnable);
        });
    }

    HudWidgetsPanel(HudEditorSession session, EditorDocumentManager documentManager,
                    Consumer<Runnable> deferUi) {
        super("Widgets");
        this.session = session;
        this.documentManager = documentManager;
        this.deferUi = deferUi;
        groupButton.addListener(change(() -> createLayout(HudNodeKind.GROUP)));
        tableButton.addListener(change(() -> createLayout(HudNodeKind.TABLE)));
        stackButton.addListener(change(() -> createLayout(HudNodeKind.STACK)));
        containerButton.addListener(change(() -> createLayout(HudNodeKind.CONTAINER)));
        scrollPaneButton.addListener(change(() -> createLayout(HudNodeKind.SCROLL_PANE)));
        windowButton.addListener(change(() -> createLayout(HudNodeKind.WINDOW)));
        dialogButton.addListener(change(() -> createLayout(HudNodeKind.DIALOG)));
        labelButton.addListener(change(this::createLabel));
        textraLabelButton.addListener(change(this::createTextraLabel));
        textButtonButton.addListener(change(this::createTextButton));
        imageButtonButton.addListener(change(this::createImageButton));
        imageTextButtonButton.addListener(change(this::createImageTextButton));
        textFieldButton.addListener(change(this::createTextField));
        selectBoxButton.addListener(change(this::createSelectBox));
        listButton.addListener(change(this::createList));
        checkBoxButton.addListener(change(this::createCheckBox));
        sliderButton.addListener(change(this::createSlider));
        progressBarButton.addListener(change(this::createProgressBar));
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setFlickScroll(false);
        scrollPane.addListener(new GetScrollListener(scrollPane));
        scrollPane.addListener(new LoseScroolListener());
        add(scrollPane).grow().minSize(0f).row();

        session.addListener(this::requestRefresh);
        documentManager.addListener(new EditorDocumentManager.Listener() {
            @Override public void documentActivated(OpenEditorDocument previous,
                                                     OpenEditorDocument current) {
                requestRefresh();
            }

            @Override public void documentTitleChanged(OpenEditorDocument document) {
                if (document == activeDocument()) requestRefresh();
            }
        });
        rebuildProjection();
    }

    public static List<HudNodeKind> layoutKinds() {
        return List.of(HudNodeKind.GROUP, HudNodeKind.TABLE, HudNodeKind.STACK, HudNodeKind.CONTAINER,
                HudNodeKind.SCROLL_PANE, HudNodeKind.WINDOW, HudNodeKind.DIALOG);
    }

    public static List<HudNodeKind> contentKinds() {
        return List.of(HudNodeKind.LABEL, HudNodeKind.TEXTRA_LABEL,
                HudNodeKind.TEXT_FIELD, HudNodeKind.TEXT_BUTTON, HudNodeKind.IMAGE_BUTTON,
                HudNodeKind.IMAGE_TEXT_BUTTON, HudNodeKind.CHECK_BOX,
                HudNodeKind.SELECT_BOX, HudNodeKind.LIST, HudNodeKind.SLIDER, HudNodeKind.PROGRESS_BAR);
    }

    public void requestRefresh() {
        if (refreshPending) return;
        refreshPending = true;
        deferUi.accept(() -> {
            if (!refreshPending) return;
            refreshPending = false;
            rebuildProjection();
        });
    }

    private void rebuildProjection() {
        if (refreshing) {
            requestRefresh();
            return;
        }
        refreshing = true;
        try {
            projectionCount++;
            content.clearChildren();
            content.top().left();
            HudScreenEditorDocument active = activeDocument();
            boolean activeHud = active != null && active.screenId().equals(session.screenId());
            boolean testMode = session.isTestMode();
            projectAuthoringButton(groupButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.GROUP));
            projectAuthoringButton(tableButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.TABLE));
            projectAuthoringButton(stackButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.STACK));
            projectAuthoringButton(containerButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.CONTAINER));
            projectAuthoringButton(scrollPaneButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.SCROLL_PANE));
            projectAuthoringButton(windowButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.WINDOW));
            projectAuthoringButton(dialogButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.DIALOG));
            addCategory("Layout", layoutCategory);
            projectAuthoringButton(labelButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.LABEL));
            projectAuthoringButton(textraLabelButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.TEXTRA_LABEL));
            projectAuthoringButton(textButtonButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.TEXT_BUTTON));
            projectAuthoringButton(imageButtonButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.IMAGE_BUTTON));
            projectAuthoringButton(imageTextButtonButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.IMAGE_TEXT_BUTTON));
            projectAuthoringButton(textFieldButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.TEXT_FIELD));
            projectAuthoringButton(selectBoxButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.SELECT_BOX));
            projectAuthoringButton(listButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.LIST));
            projectAuthoringButton(checkBoxButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.CHECK_BOX));
            projectAuthoringButton(sliderButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.SLIDER));
            projectAuthoringButton(progressBarButton, testMode || !activeHud || !session.isWidgetAvailable(HudNodeKind.PROGRESS_BAR));
            addCategory("Text", textCategory);
            addCategory("Buttons", buttonCategory);
            addCategory("Selection & values", selectionCategory);
        } finally {
            refreshing = false;
        }
    }

    private void createLayout(HudNodeKind childKind) {
        HudScreenEditorDocument active = activeDocument();
        if (active == null) return;
        if (!active.screenId().equals(session.screenId())) return;
        if (childKind == HudNodeKind.TABLE) {
            HudEditorSession.WidgetDropTarget target = session.selectedTableTarget();
            HudTableCreateDialog.show(getStage(), (rows, columns) -> {
                if (session.createTableAt(rows, columns, target, active)) requestRefresh();
            });
            return;
        }
        if (session.createSelectedWidget(childKind)) requestRefresh();
    }

    private void createLabel() {
        HudScreenEditorDocument active = activeDocument();
        if (active == null || !active.screenId().equals(session.screenId())) return;
        if (session.createLabel()) requestRefresh();
    }

    private void createTextraLabel() {
        HudScreenEditorDocument active = activeDocument();
        if (active == null || !active.screenId().equals(session.screenId())) return;
        if (session.createTextraLabel()) requestRefresh();
    }

    private void createTextButton() {
        HudScreenEditorDocument active = activeDocument();
        if (active == null || !active.screenId().equals(session.screenId())) return;
        if (session.createTextButton()) requestRefresh();
    }

    private void createImageButton() {
        HudScreenEditorDocument active = activeDocument();
        if (active == null || !active.screenId().equals(session.screenId())) return;
        if (session.createImageButton()) requestRefresh();
    }

    private void createImageTextButton() {
        HudScreenEditorDocument active = activeDocument();
        if (active == null || !active.screenId().equals(session.screenId())) return;
        if (session.createImageTextButton()) requestRefresh();
    }

    private void createTextField() {
        HudScreenEditorDocument active = activeDocument();
        if (active == null || !active.screenId().equals(session.screenId())) return;
        if (session.createTextField()) requestRefresh();
    }

    private void createSelectBox() {
        HudScreenEditorDocument active = activeDocument();
        if (active == null || !active.screenId().equals(session.screenId())) return;
        if (session.createSelectBox()) requestRefresh();
    }

    private void createList() {
        HudScreenEditorDocument active = activeDocument();
        if (active == null || !active.screenId().equals(session.screenId())) return;
        if (session.createList()) requestRefresh();
    }

    private void createCheckBox() {
        HudScreenEditorDocument active = activeDocument();
        if (active == null || !active.screenId().equals(session.screenId())) return;
        if (session.createCheckBox()) requestRefresh();
    }

    private void createSlider() {
        HudScreenEditorDocument active = activeDocument();
        if (active == null || !active.screenId().equals(session.screenId())) return;
        if (session.createSlider()) requestRefresh();
    }

    private void createProgressBar() {
        HudScreenEditorDocument active = activeDocument();
        if (active == null || !active.screenId().equals(session.screenId())) return;
        if (session.createProgressBar()) requestRefresh();
    }

    private static void projectAuthoringButton(ImageButton button, boolean disabled) {
        button.setProgrammaticChangeEvents(false);
        button.setChecked(false);
        button.setDisabled(disabled);
        button.setProgrammaticChangeEvents(true);
    }

    private void addCategory(String title, Container<HorizontalGroup> buttons) {
        content.add(new VisLabel(title)).minWidth(0f).growX().left().pad(10f, 8f, 4f, 8f).row();
        content.add(buttons).growX().maxWidth(maxCategoryWidth()).left().pad(3f, 8f, 3f, 8f).row();
    }

    private static Container<HorizontalGroup> categoryBody(HorizontalGroup buttons) {
        Container<HorizontalGroup> body = new Container<HorizontalGroup>(buttons) {
            @Override public float getMinWidth() { return 0f; }
        };
        body.left().fillX();
        return body;
    }

    static HorizontalGroup wrappingButtonGroup(Actor... buttons) {
        HorizontalGroup group = new HorizontalGroup() {
            @Override public float getMinWidth() { return 0f; }
            @Override public float getPrefWidth() { return 0f; }
        };
        group.left().top().wrap(true).space(CommonLayout.HUD_WIDGET_GRID_GAP)
                .wrapSpace(CommonLayout.HUD_WIDGET_GRID_GAP);
        for (Actor button : buttons) group.addActor(button);
        return group;
    }

    static float maxCategoryWidth() {
        return MAX_CATEGORY_COLUMNS * CommonLayout.HUD_WIDGET_BUTTON_SIZE
                + (MAX_CATEGORY_COLUMNS - 1) * CommonLayout.HUD_WIDGET_GRID_GAP;
    }

    private static ImageButton authoringButton(String iconKey, String tooltipText) {
        Skin skin = VisUI.getSkin();
        Drawable icon = skin.getDrawable(iconKey);
        VisImageButton.VisImageButtonStyle buttonStyle =
                skin.get("default", VisImageButton.VisImageButtonStyle.class);
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle(buttonStyle);
        style.imageUp = icon;
        style.imageOver = skin.newDrawable(icon, ICON_OVER_TINT);
        style.imageDown = skin.newDrawable(icon, ICON_DOWN_TINT);
        style.imageChecked = icon;
        style.imageCheckedOver = style.imageOver;
        style.imageCheckedDown = style.imageDown;
        style.imageDisabled = skin.newDrawable(icon, ICON_DISABLED_TINT);

        ImageButton button = new ImageButton(style) {
            @Override public float getPrefWidth() { return CommonLayout.HUD_WIDGET_BUTTON_SIZE; }
            @Override public float getPrefHeight() { return CommonLayout.HUD_WIDGET_BUTTON_SIZE; }
        };
        button.getImage().setScaling(Scaling.fit);
        button.getImageCell().size(CommonLayout.HUD_WIDGET_ICON_SIZE);
        Tooltip tooltip = new Tooltip.Builder(tooltipText).target(button).build();
        tooltip.setAppearDelayTime(0f);
        return button;
    }

    private HudScreenEditorDocument activeDocument() {
        return documentManager.activeDocument() instanceof HudScreenEditorDocument hud ? hud : null;
    }

    ImageButton groupButton() { return groupButton; }
    ImageButton tableButton() { return tableButton; }
    ImageButton stackButton() { return stackButton; }
    ImageButton containerButton() { return containerButton; }
    ImageButton scrollPaneButton() { return scrollPaneButton; }
    ImageButton windowButton() { return windowButton; }
    ImageButton dialogButton() { return dialogButton; }
    ImageButton labelButton() { return labelButton; }
    ImageButton textraLabelButton() { return textraLabelButton; }
    ImageButton textButtonButton() { return textButtonButton; }
    ImageButton imageButtonButton() { return imageButtonButton; }
    ImageButton imageTextButtonButton() { return imageTextButtonButton; }
    ImageButton textFieldButton() { return textFieldButton; }
    ImageButton selectBoxButton() { return selectBoxButton; }
    ImageButton listButton() { return listButton; }
    ImageButton checkBoxButton() { return checkBoxButton; }
    ImageButton sliderButton() { return sliderButton; }
    ImageButton progressBarButton() { return progressBarButton; }
    VisTable content() { return content; }
    HorizontalGroup layoutButtons() { return layoutButtons; }
    HorizontalGroup textButtons() { return textButtons; }
    HorizontalGroup buttonButtons() { return buttonButtons; }
    HorizontalGroup selectionButtons() { return selectionButtons; }
    VisScrollPane scrollPane() { return scrollPane; }
    int projectionCount() { return projectionCount; }
    boolean refreshPending() { return refreshPending; }

    private static String display(HudNodeKind kind) {
        return kind == HudNodeKind.TEXT_BUTTON ? "Text Button"
                : kind == HudNodeKind.TEXTRA_LABEL ? "Textra Label"
                : kind == HudNodeKind.IMAGE_BUTTON ? "Image Button"
                : kind == HudNodeKind.TEXT_FIELD ? "Text Field"
                : kind == HudNodeKind.SELECT_BOX ? "Select Box"
                : kind == HudNodeKind.CHECK_BOX ? "Check Box"
                : kind.name().charAt(0) + kind.name().substring(1).toLowerCase();
    }

    private static ChangeListener change(Runnable action) {
        return new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { action.run(); }
        };
    }
}
