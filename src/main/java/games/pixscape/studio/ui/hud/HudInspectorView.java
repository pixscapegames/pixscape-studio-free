package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.kotcrab.vis.ui.widget.Separator;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import com.kotcrab.vis.ui.widget.VisList;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudTableCell;
import games.pixscape.runtime.hud.document.HudHorizontalAlign;
import games.pixscape.runtime.hud.document.HudFontReferences;
import games.pixscape.runtime.hud.document.HudHorizontalAnchor;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudWindowData;
import games.pixscape.runtime.hud.document.HudWindowAction;
import games.pixscape.runtime.hud.document.HudWindowActionKind;
import games.pixscape.runtime.hud.document.HudPlacementKind;
import games.pixscape.runtime.hud.document.HudSliderOrientation;
import games.pixscape.runtime.hud.document.HudTooltipData;
import games.pixscape.runtime.hud.document.HudVerticalAlign;
import games.pixscape.runtime.hud.document.HudVerticalAnchor;
import games.pixscape.studio.service.hud.HudEditorSession;
import games.pixscape.studio.service.hud.HudEditRejectedException;
import games.pixscape.studio.service.hud.HudInspectorProjection;
import games.pixscape.studio.service.hud.HudLayoutAuthoring;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.SimpleFloatField;
import games.pixscape.studio.ui.widget.SimpleTextArea;
import games.pixscape.studio.ui.widget.SimpleTextField;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Editable HUD-node Properties view backed exclusively by the HUD document edit session. */
public final class HudInspectorView extends VisTable {
    private static final float PROPERTY_CELL_PAD = 5f;

    private final HudEditorSession session;
    private final SelectBoxItemsEditorState selectBoxItemsEditorState = new SelectBoxItemsEditorState();
    private String listEditorNodeId;
    private String listEditorScreenId;
    private int listEditorIndex;
    private String fontDiagnosticScreenId;
    private String fontDiagnosticNodeId;
    private String fontDiagnostic;
    private String imageDiagnosticScreenId;
    private String imageDiagnosticNodeId;
    private String imageDiagnosticControlName;
    private String imageDiagnostic;
    private String skinDiagnosticScreenId;
    private String skinDiagnostic;
    private String sliderDiagnosticScreenId;
    private String sliderDiagnosticNodeId;
    private String sliderDiagnostic;
    private String progressBarDiagnosticScreenId;
    private String progressBarDiagnosticNodeId;
    private String progressBarDiagnostic;
    private String scrollPaneDiagnosticScreenId;
    private String scrollPaneDiagnosticNodeId;
    private String scrollPaneDiagnostic;
    private String tooltipDiagnosticScreenId;
    private String tooltipDiagnosticNodeId;
    private String tooltipDiagnosticControl;
    private String tooltipDiagnostic;
    private String windowDiagnosticScreenId;
    private String windowDiagnosticNodeId;
    private String windowDiagnostic;
    private String visibilityDiagnosticScreenId;
    private String visibilityDiagnosticNodeId;
    private String visibilityDiagnostic;
    private boolean refreshing;
    private String resultDiagnosticScreenId;
    private String resultDiagnosticNodeId;
    private String resultDiagnostic;
    private HudDocumentV1 resultDiagnosticDocument;

    public HudInspectorView(HudEditorSession session) {
        this.session = session;
        top().left();
        defaults().left().top().pad(PROPERTY_CELL_PAD);
        addCaptureListener(new InputListener() {
            @Override public boolean touchDown(InputEvent event, float x, float y,
                                               int pointer, int button) {
                if (!session.isTestMode()) return false;
                event.stop();
                return true;
            }
            @Override public boolean keyDown(InputEvent event, int keycode) {
                if (!session.isTestMode()) return false;
                event.stop();
                return true;
            }
            @Override public boolean scrolled(InputEvent event, float x, float y,
                                              float amountX, float amountY) {
                return session.isTestMode();
            }
        });
        session.addListener(this::rebuild);
        rebuild();
    }

    public void rebuild() {
        refreshing = true;
        try {
            clearChildren();
            HudNode node = session.selectedNode();
            HudTableCell selectedCell = session.selectedCell();
            clearStaleFontDiagnostic(node);
            clearStaleImageDiagnostic(node);
            clearStaleSliderDiagnostic(node);
            clearStaleProgressBarDiagnostic(node);
            clearStaleScrollPaneDiagnostic(node);
            clearStaleTooltipDiagnostic(node);
            clearStaleWindowDiagnostic(node);
            clearStaleVisibilityDiagnostic(node);
            if (node == null || !Objects.equals(resultDiagnosticScreenId, session.screenId())
                    || !Objects.equals(resultDiagnosticNodeId, node.id)
                    || resultDiagnosticDocument != session.document()) resultDiagnostic = null;
            add(new VisLabel(selectedCell != null ? "CELL" : titleFor(node))).center().colspan(2)
                    .padBottom(CommonLayout.PROPERTY_SECTION_TITLE_BOTTOM_PAD).row();
            if (selectedCell != null) addSelectedCellProperties(selectedCell);
            else if (node == null) addScreenProperties();
            else addNodeProperties(node);
            addError();
        } finally {
            refreshing = false;
        }
        invalidateHierarchy();
    }

    static String titleFor(HudNode node) {
        if (node == null) return "HUD SCREEN";
        return node.kind == HudNodeKind.TEXT_BUTTON
                ? "TEXTBUTTON" : node.kind == HudNodeKind.IMAGE_BUTTON
                ? "IMAGEBUTTON" : node.kind == HudNodeKind.TEXT_FIELD
                ? "TEXTFIELD" : node.kind == HudNodeKind.SELECT_BOX
                ? "SELECTBOX" : node.kind == HudNodeKind.CHECK_BOX
                ? "CHECKBOX" : node.kind.name().replace('_', ' ');
    }

    private void addScreenProperties() {
        for (HudInspectorProjection.Field field : HudInspectorProjection.screen(session)) {
            if ("Skin".equals(field.label())) addSkinChoice();
            else addReadOnly(field.label(), field.value());
        }
    }

    private void addSkinChoice() {
        String originScreenId = session.screenId();
        String currentSkinId = session.asset() != null ? session.asset().skinId : null;
        List<SkinChoice> options = new java.util.ArrayList<>();
        options.add(new SkinChoice(null, null, "None", false));
        for (HudEditorSession.SkinResourceOption option : session.skinResourceOptions()) {
            options.add(new SkinChoice(option.assetId(), option.skinId(), option.label(), false));
        }
        SkinChoice current = skinChoice(options, currentSkinId);
        if (current == null) {
            current = new SkinChoice(null, currentSkinId,
                    "Missing: " + currentSkinId, true);
            options.add(current);
        }

        VisSelectBox<Choice<SkinChoice>> box = new VisSelectBox<>();
        box.setName("hudScreenSkin");
        @SuppressWarnings("unchecked") Choice<SkinChoice>[] choices = new Choice[options.size()];
        for (int index = 0; index < options.size(); index++) {
            SkinChoice option = options.get(index);
            choices[index] = new Choice<>(option, option.label());
        }
        box.setItems(choices);
        for (Choice<SkinChoice> choice : choices) {
            if (choice.value.equals(current)) {
                box.setSelected(choice);
                break;
            }
        }
        box.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (refreshing || !Objects.equals(originScreenId, session.screenId())) return;
                Choice<SkinChoice> selected = box.getSelected();
                if (selected == null) return;
                if (selected.value.missing()) {
                    setSkinDiagnostic(originScreenId,
                            "The referenced Skin Asset is missing: " + selected.value.skinId());
                    rebuild();
                    return;
                }
                clearSkinDiagnostic(originScreenId);
                try {
                    if (!session.assignSkin(originScreenId, selected.value.assetId())) rebuild();
                } catch (HudEditRejectedException rejected) {
                    setSkinDiagnostic(originScreenId, rejected.getMessage());
                    rebuild();
                }
            }
        });
        addFormLabel("Skin");
        add(box).growX().left().row();
        if (hasSkinDiagnostic(originScreenId)) {
            VisLabel diagnostic = new VisLabel(skinDiagnostic);
            diagnostic.setName("hudScreenSkinError");
            diagnostic.setWrap(true);
            diagnostic.setColor(1f, .55f, .55f, 1f);
            add(diagnostic).growX().colspan(2).padTop(2f).row();
        }
    }

    private static SkinChoice skinChoice(List<SkinChoice> options, String skinId) {
        for (SkinChoice option : options) {
            if (Objects.equals(option.skinId(), skinId)) return option;
        }
        return null;
    }

    private void setSkinDiagnostic(String screenId, String diagnostic) {
        skinDiagnosticScreenId = screenId;
        skinDiagnostic = diagnostic;
    }

    private void clearSkinDiagnostic(String screenId) {
        if (!Objects.equals(skinDiagnosticScreenId, screenId)) return;
        skinDiagnosticScreenId = null;
        skinDiagnostic = null;
    }

    private boolean hasSkinDiagnostic(String screenId) {
        return Objects.equals(skinDiagnosticScreenId, screenId)
                && skinDiagnostic != null && !skinDiagnostic.isBlank();
    }

    private void addNodeProperties(HudNode node) {
        String nodeId = node.id;
        HudDocumentV1 currentDocument = session.document();
        HudChild relation = HudLayoutAuthoring.childRelation(currentDocument, nodeId);
        HudTableCell ownerCell = HudLayoutAuthoring.containingCell(session.document(), nodeId);
        HudNode resultOwner = HudLayoutAuthoring.resultButtonOwner(session.document(), nodeId);
        HudPlacementKind placement = relation != null ? relation.placementKind
                : ownerCell != null ? HudPlacementKind.CELL
                : resultOwner != null ? HudPlacementKind.DIRECT : null;
        addReadOnly("Node ID", nodeId);
        addReadOnly("Placement", resultOwner != null ? "DIALOG BUTTON TABLE"
                : placement == null ? "ROOT" : placement.name());
        if (node.kind != HudNodeKind.DIALOG) {
            addVisibilityProperty(session.screenId(), nodeId, node.visible);
        }
        if (placement == null || placement == HudPlacementKind.FREE) {
            addNumber("Width", "hudWidthField", nodeId, nodeValue(nodeId, current -> current.actor.width),
                    value -> edit(nodeId, (current, ignored) -> current.actor.width = value), 1, nonNegative());
            addNumber("Height", "hudHeightField", nodeId, nodeValue(nodeId, current -> current.actor.height),
                    value -> edit(nodeId, (current, ignored) -> current.actor.height = value), 1, nonNegative());
        } else {
            addReadOnly("Layout", "Controlled by parent " + placement.name().toLowerCase());
        }

        if (placement == HudPlacementKind.FREE) {
            addFreeProperties(nodeId);
            if (node.kind == HudNodeKind.DIALOG) {
                addReadOnly("Center on HUD", "Center anchors, 0.5 pivots, zero offsets");
            }
        }
        if (node.kind == HudNodeKind.TABLE && relation != null
                && currentDocument.root.children.contains(relation)) {
            addCheck("Fill HUD surface", "hudTableFillParent", nodeId,
                    nodeValue(nodeId, current -> current.fillParent),
                    (current, currentRelation, value) -> {
                        current.fillParent = value;
                        currentRelation.placementKind = value
                                ? HudPlacementKind.DIRECT : HudPlacementKind.FREE;
                        currentRelation.free = value ? null
                                : new games.pixscape.runtime.hud.document.HudFreePlacement();
                        current.actor.width = value ? 0f : CommonLayout.DEFAULT_FREE_WIDTH;
                        current.actor.height = value ? 0f : CommonLayout.DEFAULT_FREE_HEIGHT;
                    });
        }
        if (placement == HudPlacementKind.DIRECT) addReadOnly("Child layout", "Controlled by parent container");
        if (node.kind == HudNodeKind.DIALOG) addDialogResultButtons(node);
        if (resultOwner != null) addDialogResultProperties(nodeId);
        addPayloadProperties(nodeId, node);
        addTooltipProperties(nodeId, node);
    }

    private void addVisibilityProperty(String screenId, String nodeId, boolean visible) {
        VisCheckBox check = new VisCheckBox("");
        check.setName("hudVisible");
        check.setChecked(visible);
        check.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (refreshing || !isCurrentTarget(screenId, nodeId)) return;
                visibilityDiagnostic = null;
                try {
                    if (!session.editNodeVisibility(screenId, nodeId, check.isChecked())) rebuild();
                } catch (HudEditRejectedException rejected) {
                    visibilityDiagnosticScreenId = screenId;
                    visibilityDiagnosticNodeId = nodeId;
                    visibilityDiagnostic = rejected.getMessage();
                    rebuild();
                }
            }
        });
        addFormLabel("Visible");
        add(check).left().row();
        if (visibilityDiagnostic != null && Objects.equals(visibilityDiagnosticScreenId, screenId)
                && Objects.equals(visibilityDiagnosticNodeId, nodeId)) {
            VisLabel diagnostic = new VisLabel(visibilityDiagnostic);
            diagnostic.setName("hudVisibleError");
            diagnostic.setWrap(true);
            diagnostic.setColor(1f, .55f, .55f, 1f);
            add(diagnostic).growX().colspan(2).padTop(2f).row();
        }
    }

    private void clearStaleVisibilityDiagnostic(HudNode node) {
        if (visibilityDiagnostic != null
                && (!Objects.equals(visibilityDiagnosticScreenId, session.screenId())
                || node == null || !Objects.equals(visibilityDiagnosticNodeId, node.id))) {
            visibilityDiagnostic = null;
        }
    }

    private void addTooltipProperties(String nodeId, HudNode node) {
        String screenId = session.screenId();
        addPropertySeparator();
        add(new VisLabel("Tooltip")).colspan(2).left().row();
        VisCheckBox enabled = new VisCheckBox("Enabled");
        enabled.setName("hudTooltipEnabled");
        enabled.setChecked(node.tooltip != null);
        enabled.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                applyTooltipEdit(screenId, nodeId, "hudTooltipEnabled",
                        "Toggle HUD tooltip", current -> current.tooltip =
                                enabled.isChecked() ? new HudTooltipData() : null);
            }
        });
        add(enabled).colspan(2).left().row();
        if (node.tooltip == null) {
            addTooltipDiagnostic(screenId, nodeId);
            return;
        }

        SimpleTextArea textField = new SimpleTextArea().withMultilineEditing();
        textField.setName("hudTooltipText");
        textField.setPrefRows(3);
        textField.bind(nodeValue(nodeId, current -> current.tooltip == null
                        ? null : current.tooltip.text), value ->
                applyTooltipEdit(screenId, nodeId, "hudTooltipText", "Edit HUD tooltip text",
                        current -> requireTooltip(current).text = value));
        installTextAreaLifecycle(textField);
        addFormLabel("Text").padLeft(CommonLayout.PAD_LEFT_SUBMENU).top();
        add(textField).growX().left().row();

        List<LabelStyleOption> styles = new java.util.ArrayList<>();
        styles.add(new LabelStyleOption(null, "Default"));
        for (String name : session.textTooltipStyleNames(node.tooltip.fontAssetId != null)) {
            styles.add(new LabelStyleOption(name, name));
        }
        if (node.tooltip.styleName != null && !node.tooltip.styleName.isBlank()
                && styles.stream().noneMatch(option -> node.tooltip.styleName.equals(option.styleName()))) {
            styles.add(new LabelStyleOption(node.tooltip.styleName, "Missing: " + node.tooltip.styleName));
        }
        VisSelectBox<Choice<LabelStyleOption>> styleBox = new VisSelectBox<>();
        styleBox.setName("hudTooltipStyle");
        @SuppressWarnings("unchecked") Choice<LabelStyleOption>[] styleChoices = new Choice[styles.size()];
        for (int i = 0; i < styles.size(); i++) {
            styleChoices[i] = new Choice<>(styles.get(i), styles.get(i).label());
        }
        styleBox.setItems(styleChoices);
        for (Choice<LabelStyleOption> option : styleChoices) {
            if (Objects.equals(option.value.styleName(), node.tooltip.styleName)) styleBox.setSelected(option);
        }
        styleBox.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (refreshing || !isCurrentTarget(screenId, nodeId)) return;
                Choice<LabelStyleOption> selected = styleBox.getSelected();
                if (selected != null) applyTooltipEdit(screenId, nodeId, "hudTooltipStyle",
                        "Edit HUD tooltip style",
                        current -> requireTooltip(current).styleName = selected.value.styleName());
            }
        });
        addFormLabel("Style").padLeft(CommonLayout.PAD_LEFT_SUBMENU);
        add(styleBox).growX().left().row();

        List<HudEditorSession.FontResourceOption> fonts = new java.util.ArrayList<>();
        fonts.add(new HudEditorSession.FontResourceOption(null, "Style font"));
        fonts.addAll(session.fontResourceOptions());
        Integer authoredFontId = node.tooltip.fontAssetId;
        if (authoredFontId != null && fonts.stream().noneMatch(option ->
                Objects.equals(option.assetId(), authoredFontId))) {
            fonts.add(new HudEditorSession.FontResourceOption(authoredFontId,
                    "Missing: " + authoredFontId));
        }
        VisSelectBox<Choice<HudEditorSession.FontResourceOption>> fontBox = new VisSelectBox<>();
        fontBox.setName("hudTooltipFont");
        @SuppressWarnings("unchecked") Choice<HudEditorSession.FontResourceOption>[] fontChoices =
                new Choice[fonts.size()];
        for (int i = 0; i < fonts.size(); i++) {
            fontChoices[i] = new Choice<>(fonts.get(i), fonts.get(i).label());
        }
        fontBox.setItems(fontChoices);
        for (Choice<HudEditorSession.FontResourceOption> option : fontChoices) {
            if (Objects.equals(option.value.assetId(), authoredFontId)) fontBox.setSelected(option);
        }
        fontBox.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (refreshing || !isCurrentTarget(screenId, nodeId)) return;
                Choice<HudEditorSession.FontResourceOption> selected = fontBox.getSelected();
                if (selected != null) applyTooltipEdit(screenId, nodeId, "hudTooltipFont",
                        "Edit HUD tooltip font",
                        current -> requireTooltip(current).fontAssetId = selected.value.assetId());
            }
        });
        addFormLabel("Font").padLeft(CommonLayout.PAD_LEFT_SUBMENU);
        add(fontBox).growX().left().row();

        addTooltipDiagnostic(screenId, nodeId);
    }

    private void addTooltipDiagnostic(String screenId, String nodeId) {
        if (Objects.equals(tooltipDiagnosticScreenId, screenId)
                && Objects.equals(tooltipDiagnosticNodeId, nodeId)
                && tooltipDiagnostic != null) {
            VisLabel diagnostic = new VisLabel(tooltipDiagnostic);
            diagnostic.setName(tooltipDiagnosticControl + "Error");
            diagnostic.setWrap(true);
            diagnostic.setColor(1f, .55f, .55f, 1f);
            add(diagnostic).growX().colspan(2).padTop(2f).row();
        }
    }

    private void applyTooltipEdit(String screenId, String nodeId, String control,
                                  String label, java.util.function.Consumer<HudNode> mutation) {
        if (refreshing || !isCurrentTarget(screenId, nodeId)) return;
        tooltipDiagnostic = null;
        try {
            if (!session.editTooltip(screenId, nodeId, label, mutation)) rebuild();
        } catch (HudEditRejectedException rejected) {
            tooltipDiagnosticScreenId = screenId;
            tooltipDiagnosticNodeId = nodeId;
            tooltipDiagnosticControl = control;
            tooltipDiagnostic = rejected.getMessage();
            rebuild();
        }
    }

    private void clearStaleTooltipDiagnostic(HudNode node) {
        if (tooltipDiagnostic == null) return;
        if (!Objects.equals(tooltipDiagnosticScreenId, session.screenId())
                || node == null || !Objects.equals(tooltipDiagnosticNodeId, node.id)) {
            tooltipDiagnostic = null;
        }
    }

    private static HudTooltipData requireTooltip(HudNode node) {
        if (node.tooltip == null) throw new HudEditRejectedException("Tooltip is no longer enabled.");
        return node.tooltip;
    }

    private void addFreeProperties(String nodeId) {
        addPropertySeparator();
        addChoice("Horizontal anchor", "hudHorizontalAnchor", nodeId,
                relationValue(nodeId, relation -> relation.free.horizontalAnchor),
                HudHorizontalAnchor.values(), HudInspectorView::enumLabel,
                (node, relation, selected) -> relation.free.horizontalAnchor = selected);
        addChoice("Vertical anchor", "hudVerticalAnchor", nodeId,
                relationValue(nodeId, relation -> relation.free.verticalAnchor),
                HudVerticalAnchor.values(), HudInspectorView::enumLabel,
                (node, relation, selected) -> relation.free.verticalAnchor = selected);
        addNumber("Pivot X", "hudPivotXField", nodeId,
                relationValue(nodeId, relation -> relation.free.pivotX),
                value -> edit(nodeId, (node, relation) -> relation.free.pivotX = value), 2, within(0f, 1f));
        addNumber("Pivot Y", "hudPivotYField", nodeId,
                relationValue(nodeId, relation -> relation.free.pivotY),
                value -> edit(nodeId, (node, relation) -> relation.free.pivotY = value), 2, within(0f, 1f));
        addNumber("Offset X", "hudOffsetXField", nodeId,
                relationValue(nodeId, relation -> relation.free.offsetX),
                value -> edit(nodeId, (node, relation) -> relation.free.offsetX = value), 0, finite());
        addNumber("Offset Y", "hudOffsetYField", nodeId,
                relationValue(nodeId, relation -> relation.free.offsetY),
                value -> edit(nodeId, (node, relation) -> relation.free.offsetY = value), 0, finite());
    }

    /** Presents the same native Cell constraint controls for an explicit, possibly empty cell. */
    private void addSelectedCellProperties(HudTableCell cell) {
        String cellId = cell.id;
        addReadOnly("Cell ID", cellId);
        addReadOnly("Content", cell.content == null ? "Empty" : cell.content.id);
        HudLayoutAuthoring.CellPosition position = HudLayoutAuthoring.cellPosition(session.document(), cellId);
        if (position != null) {
            String columns = position.colspan() == 1 ? String.valueOf(position.column() + 1)
                    : (position.column() + 1) + "–" + position.endColumn();
            addReadOnly("Position", "Row " + (position.row() + 1) + ", column " + columns);
        }
        if (session.selectedCellRange().size() > 1)
            addReadOnly("Selection", session.selectedCellRange().size() + " cells in row");
        addPropertySeparator();
        SimpleFloatField minWidth = cellNumberField("hudMinWidthField", cellId,
                current -> current.constraints.minWidth,
                (current, value) -> current.constraints.minWidth = value, 1, nonNegative());
        SimpleFloatField minHeight = cellNumberField("hudMinHeightField", cellId,
                current -> current.constraints.minHeight,
                (current, value) -> current.constraints.minHeight = value, 1, nonNegative());
        addCellChecks("Natural minimum", "hudMinWidthAuto", "hudMinHeightAuto", cellId,
                current -> current.constraints.minWidth == null,
                current -> current.constraints.minHeight == null,
                (current, widthAuto, heightAuto) -> {
                    current.constraints.minWidth = widthAuto ? null
                            : current.constraints.minWidth == null ? 0f : current.constraints.minWidth;
                    current.constraints.minHeight = heightAuto ? null
                            : current.constraints.minHeight == null ? 0f : current.constraints.minHeight;
                });
        addPair("Minimum", "Width", minWidth, "Height", minHeight);
        SimpleFloatField prefWidth = cellNumberField("hudPrefWidthField", cellId,
                current -> current.constraints.prefWidth,
                (current, value) -> current.constraints.prefWidth = value, 1, nonNegative());
        SimpleFloatField prefHeight = cellNumberField("hudPrefHeightField", cellId,
                current -> current.constraints.prefHeight,
                (current, value) -> current.constraints.prefHeight = value, 1, nonNegative());
        addCellChecks("Natural size", "hudPrefWidthAuto", "hudPrefHeightAuto", cellId,
                current -> current.constraints.prefWidth == null,
                current -> current.constraints.prefHeight == null,
                (current, widthAuto, heightAuto) -> {
                    current.constraints.prefWidth = widthAuto ? null
                            : current.constraints.prefWidth == null ? 0f : current.constraints.prefWidth;
                    current.constraints.prefHeight = heightAuto ? null
                            : current.constraints.prefHeight == null ? 0f : current.constraints.prefHeight;
                });
        addPair("Preferred", "Width", prefWidth, "Height", prefHeight);
        SimpleFloatField maxWidth = cellNumberField("hudMaxWidthField", cellId,
                current -> current.constraints.maxWidth,
                (current, value) -> current.constraints.maxWidth = value, 1, nonNegative());
        SimpleFloatField maxHeight = cellNumberField("hudMaxHeightField", cellId,
                current -> current.constraints.maxHeight,
                (current, value) -> current.constraints.maxHeight = value, 1, nonNegative());
        addCellChecks("No maximum", "hudMaxWidthAuto", "hudMaxHeightAuto", cellId,
                current -> current.constraints.maxWidth == null,
                current -> current.constraints.maxHeight == null,
                (current, widthAuto, heightAuto) -> {
                    current.constraints.maxWidth = widthAuto ? null
                            : current.constraints.maxWidth == null ? 0f : current.constraints.maxWidth;
                    current.constraints.maxHeight = heightAuto ? null
                            : current.constraints.maxHeight == null ? 0f : current.constraints.maxHeight;
                });
        addPair("Maximum", "Width", maxWidth, "Height", maxHeight);
        SimpleFloatField padLeft = cellNumberField("hudPadLeftField", cellId,
                current -> current.constraints.padLeft,
                (current, value) -> current.constraints.padLeft = value, 1, nonNegative());
        SimpleFloatField padRight = cellNumberField("hudPadRightField", cellId,
                current -> current.constraints.padRight,
                (current, value) -> current.constraints.padRight = value, 1, nonNegative());
        SimpleFloatField padTop = cellNumberField("hudPadTopField", cellId,
                current -> current.constraints.padTop,
                (current, value) -> current.constraints.padTop = value, 1, nonNegative());
        SimpleFloatField padBottom = cellNumberField("hudPadBottomField", cellId,
                current -> current.constraints.padBottom,
                (current, value) -> current.constraints.padBottom = value, 1, nonNegative());
        addPadding(padLeft, padRight, padTop, padBottom);
        addCellChecks("Fill", "hudFillX", "hudFillY", cellId,
                current -> current.constraints.fillX, current -> current.constraints.fillY,
                (current, x, y) -> { current.constraints.fillX = x; current.constraints.fillY = y; });
        addCellChecks("Expand", "hudExpandX", "hudExpandY", cellId,
                current -> current.constraints.expandX, current -> current.constraints.expandY,
                (current, x, y) -> { current.constraints.expandX = x; current.constraints.expandY = y; });
        addCellChoice("Horizontal align", "hudHorizontalAlign", cellId,
                current -> current.constraints.horizontalAlign, HudHorizontalAlign.values(),
                HudInspectorView::enumLabel,
                (current, selected) -> current.constraints.horizontalAlign = selected);
        addCellChoice("Vertical align", "hudVerticalAlign", cellId,
                current -> current.constraints.verticalAlign, HudVerticalAlign.values(),
                HudInspectorView::enumLabel,
                (current, selected) -> current.constraints.verticalAlign = selected);
    }

    private void addDialogResultButtons(HudNode dialog) {
        String screenId = session.screenId();
        String dialogId = dialog.id;
        addPropertySeparator();
        add(new VisLabel("Result buttons")).left().colspan(2).row();
        VisSelectBox<HudNodeKind> kind = new VisSelectBox<>();
        kind.setName("hudDialogResultKind");
        kind.setItems(HudNodeKind.TEXT_BUTTON, HudNodeKind.IMAGE_BUTTON,
                HudNodeKind.IMAGE_TEXT_BUTTON);
        VisTextButton addButton = new VisTextButton("Add button");
        addButton.setName("hudAddDialogResultButton");
        addButton.addListener(change(() -> {
            if (isCurrentTarget(screenId, dialogId))
                session.addDialogResultButton(dialogId, kind.getSelected());
        }));
        add(kind).left();
        add(addButton).left().row();
        for (int i = 0; i < dialog.dialog.resultButtons.size(); i++) {
            var entry = dialog.dialog.resultButtons.get(i);
            String buttonId = entry.button.id;
            final int index = i;
            addReadOnly("Result " + (i + 1), entry.resultId);
            VisTextButton select = new VisTextButton("Select");
            select.setName("hudSelectDialogResult" + i);
            select.addListener(change(() -> {
                if (isCurrentTarget(screenId, dialogId))
                    session.selectNode(buttonId);
            }));
            VisTextButton up = new VisTextButton("Up");
            up.setName("hudMoveDialogResultUp" + i);
            up.setDisabled(i == 0);
            up.addListener(change(() -> {
                if (isCurrentTarget(screenId, dialogId)) {
                    session.moveDialogResultButton(dialogId, buttonId, -1);
                    rebuild();
                }
            }));
            VisTextButton down = new VisTextButton("Down");
            down.setName("hudMoveDialogResultDown" + i);
            down.setDisabled(i == dialog.dialog.resultButtons.size() - 1);
            down.addListener(change(() -> {
                if (isCurrentTarget(screenId, dialogId)) {
                    session.moveDialogResultButton(dialogId, buttonId, 1);
                    rebuild();
                }
            }));
            VisTextButton remove = new VisTextButton("Delete");
            remove.setName("hudDeleteDialogResult" + i);
            remove.addListener(change(() -> {
                if (isCurrentTarget(screenId, dialogId))
                    session.deleteNode(buttonId);
            }));
            VisTable controls = new VisTable();
            controls.defaults().pad(PROPERTY_CELL_PAD);
            controls.add(select);
            controls.add(up);
            controls.add(down);
            controls.add(remove);
            addFormLabel("Button " + (index + 1));
            add(controls).left().row();
        }
    }

    private void addDialogResultProperties(String buttonId) {
        String screenId = session.screenId();
        addPropertySeparator();
        add(new VisLabel("Dialog result")).left().colspan(2).row();
        addText("Result ID", "hudDialogResultId", buttonId,
                () -> {
                    var entry = HudLayoutAuthoring.resultButton(session.document(), buttonId);
                    return entry != null ? entry.resultId : null;
                }, value -> {
                    resultDiagnostic = null;
                    try {
                        if (!session.editSelectedDialogResultButton("Edit HUD Dialog result ID",
                                entry -> entry.resultId = value)) rebuild();
                    } catch (HudEditRejectedException rejected) {
                        resultDiagnosticScreenId = screenId;
                        resultDiagnosticNodeId = buttonId;
                        resultDiagnostic = rejected.getMessage();
                        resultDiagnosticDocument = session.document();
                        rebuild();
                    }
                });
        VisCheckBox close = new VisCheckBox("Close after activation");
        close.setName("hudDialogResultClose");
        var entry = HudLayoutAuthoring.resultButton(session.document(), buttonId);
        close.setChecked(entry != null && entry.closeAfterActivation);
        close.addListener(change(() -> {
            if (!refreshing && isCurrentTarget(screenId, buttonId))
                session.editSelectedDialogResultButton("Edit HUD Dialog result closing",
                        current -> current.closeAfterActivation = close.isChecked());
        }));
        addFormLabel("");
        add(close).left().row();
        if (resultDiagnostic != null && Objects.equals(resultDiagnosticScreenId, screenId)
                && Objects.equals(resultDiagnosticNodeId, buttonId)) {
            VisLabel diagnostic = new VisLabel(resultDiagnostic);
            diagnostic.setName("hudDialogResultError");
            diagnostic.setWrap(true);
            diagnostic.setColor(1f, .55f, .55f, 1f);
            add(diagnostic).growX().colspan(2).row();
        }
    }

    private void addPayloadProperties(String nodeId, HudNode node) {
        if (node.label != null) {
            addMultilineText("Text", "hudTextField", nodeId,
                    nodeValue(nodeId, current -> current.label.text),
                    value -> edit(nodeId, (current, ignored) -> current.label.text = value));
            List<LabelStyleOption> styles = new java.util.ArrayList<>();
            styles.add(new LabelStyleOption(null, "Default"));
            for (String style : session.labelStyleNames(node.label.fontAssetId != null)) {
                styles.add(new LabelStyleOption(style, style));
            }
            addChoice("Style", "hudLabelStyle", nodeId,
                    nodeValue(nodeId, current -> labelStyleOption(current.label.styleName)),
                    styles.toArray(new LabelStyleOption[0]), LabelStyleOption::label,
                    (current, ignored, selected) -> current.label.styleName = selected.styleName());
            addFontChoice(nodeId, "hudLabelFont");
        }
        if (node.textraLabel != null) {
            addMultilineText("Text", "hudTextraLabelText", nodeId,
                    nodeValue(nodeId, current -> current.textraLabel.text),
                    value -> edit(nodeId,
                            (current, ignored) -> current.textraLabel.text = value));
            List<LabelStyleOption> styles = new java.util.ArrayList<>();
            styles.add(new LabelStyleOption(null, "Default"));
            for (String style : session.labelStyleNames(
                    node.textraLabel.fontAssetId != null)) {
                styles.add(new LabelStyleOption(style, style));
            }
            addChoice("Style", "hudTextraLabelStyle", nodeId,
                    nodeValue(nodeId,
                            current -> labelStyleOption(current.textraLabel.styleName)),
                    styles.toArray(new LabelStyleOption[0]), LabelStyleOption::label,
                    (current, ignored, selected) ->
                            current.textraLabel.styleName = selected.styleName());
            addFontChoice(nodeId, "hudTextraLabelFont");
            addCheck("Typing", "hudTextraLabelTypingEnabled", nodeId,
                    nodeValue(nodeId, current -> current.textraLabel.typingEnabled),
                    (current, ignored, checked) ->
                            current.textraLabel.typingEnabled = checked);
            VisTextButton preview = new VisTextButton("Preview typing");
            preview.setName("hudTextraLabelPreviewTyping");
            preview.setDisabled(!node.textraLabel.typingEnabled);
            preview.addListener(change(() -> {
                if (!refreshing && isCurrentTarget(nodeId)) {
                    session.previewSelectedTextraTyping();
                }
            }));
            addFormLabel("Preview");
            add(preview).left().row();
        }
        if (node.textButton != null) {
            addMultilineText("Text", "hudTextField", nodeId,
                    nodeValue(nodeId, current -> current.textButton.text),
                    value -> { if (isCurrentTarget(nodeId)) session.editSelectedTextButtonText(value); });
            List<LabelStyleOption> styles = new java.util.ArrayList<>();
            styles.add(new LabelStyleOption(null, "Default"));
            for (String style : session.textButtonStyleNames(
                    node.textButton.fontAssetId != null)) {
                styles.add(new LabelStyleOption(style, style));
            }
            addDirectChoice("Style", "hudTextButtonStyle", nodeId,
                    nodeValue(nodeId, current -> labelStyleOption(current.textButton.styleName)),
                    styles.toArray(new LabelStyleOption[0]), LabelStyleOption::label,
                    selected -> session.editSelectedTextButtonStyle(selected.styleName()));
            addFontChoice(nodeId, "hudTextButtonFont");
        }
        if (node.imageButton != null) {
            List<LabelStyleOption> styles = new java.util.ArrayList<>();
            styles.add(new LabelStyleOption(null, "Default"));
            for (String style : session.imageButtonStyleNames()) {
                styles.add(new LabelStyleOption(style, style));
            }
            addDirectChoice("Style", "hudImageButtonStyle", nodeId,
                    nodeValue(nodeId, current -> labelStyleOption(current.imageButton.styleName)),
                    styles.toArray(new LabelStyleOption[0]), LabelStyleOption::label,
                    selected -> session.editSelectedImageButtonStyle(selected.styleName()));
            List<HudEditorSession.ImageResourceOption> imageOptions =
                    imageButtonImageOptions(node.imageButton);
            addImageButtonImageChoice(nodeId, HudEditorSession.ImageButtonImageSlot.UP,
                    "hudImageButtonImageUp", imageOptions);
            addPropertySeparator();
            add(new VisLabel("State images")).left().colspan(2).row();
            addImageButtonImageChoice(nodeId, HudEditorSession.ImageButtonImageSlot.DOWN,
                    "hudImageButtonImageDown", imageOptions);
            addImageButtonImageChoice(nodeId, HudEditorSession.ImageButtonImageSlot.OVER,
                    "hudImageButtonImageOver", imageOptions);
            addImageButtonImageChoice(nodeId, HudEditorSession.ImageButtonImageSlot.DISABLED,
                    "hudImageButtonImageDisabled", imageOptions);
            addImageButtonImageChoice(nodeId, HudEditorSession.ImageButtonImageSlot.CHECKED,
                    "hudImageButtonImageChecked", imageOptions);
            addImageButtonImageChoice(nodeId, HudEditorSession.ImageButtonImageSlot.CHECKED_DOWN,
                    "hudImageButtonImageCheckedDown", imageOptions);
            addImageButtonImageChoice(nodeId, HudEditorSession.ImageButtonImageSlot.CHECKED_OVER,
                    "hudImageButtonImageCheckedOver", imageOptions);
        }
        if (node.imageTextButton != null) {
            addMultilineText("Text", "hudImageTextButtonText", nodeId,
                    nodeValue(nodeId, current -> current.imageTextButton.text),
                    value -> { if (isCurrentTarget(nodeId)) session.editSelectedImageTextButtonText(value); });
            List<LabelStyleOption> styles = new java.util.ArrayList<>();
            styles.add(new LabelStyleOption(null, "Default"));
            for (String style : session.imageTextButtonStyleNames(
                    node.imageTextButton.fontAssetId != null)) {
                styles.add(new LabelStyleOption(style, style));
            }
            addDirectChoice("Style", "hudImageTextButtonStyle", nodeId,
                    nodeValue(nodeId, current -> labelStyleOption(current.imageTextButton.styleName)),
                    styles.toArray(new LabelStyleOption[0]), LabelStyleOption::label,
                    selected -> session.editSelectedImageTextButtonStyle(selected.styleName()));
            addFontChoice(nodeId, "hudImageTextButtonFont");
            List<HudEditorSession.ImageResourceOption> imageOptions =
                    imageTextButtonImageOptions(node.imageTextButton);
            addImageTextButtonImageChoice(nodeId, HudEditorSession.ImageButtonImageSlot.UP,
                    "hudImageTextButtonImageUp", imageOptions);
            addPropertySeparator();
            add(new VisLabel("State images")).left().colspan(2).row();
            addImageTextButtonImageChoice(nodeId, HudEditorSession.ImageButtonImageSlot.DOWN,
                    "hudImageTextButtonImageDown", imageOptions);
            addImageTextButtonImageChoice(nodeId, HudEditorSession.ImageButtonImageSlot.OVER,
                    "hudImageTextButtonImageOver", imageOptions);
            addImageTextButtonImageChoice(nodeId, HudEditorSession.ImageButtonImageSlot.DISABLED,
                    "hudImageTextButtonImageDisabled", imageOptions);
            addImageTextButtonImageChoice(nodeId, HudEditorSession.ImageButtonImageSlot.CHECKED,
                    "hudImageTextButtonImageChecked", imageOptions);
            addImageTextButtonImageChoice(nodeId, HudEditorSession.ImageButtonImageSlot.CHECKED_DOWN,
                    "hudImageTextButtonImageCheckedDown", imageOptions);
            addImageTextButtonImageChoice(nodeId, HudEditorSession.ImageButtonImageSlot.CHECKED_OVER,
                    "hudImageTextButtonImageCheckedOver", imageOptions);
        }
        if (node.textField != null) {
            addText("Text", "hudTextFieldText", nodeId,
                    nodeValue(nodeId, current -> current.textField.text),
                    value -> edit(nodeId, (current, ignored) -> current.textField.text = value));
            addText("Placeholder", "hudTextFieldPlaceholder", nodeId,
                    nodeValue(nodeId, current -> current.textField.messageText),
                    value -> edit(nodeId,
                            (current, ignored) -> current.textField.messageText = value));
            List<LabelStyleOption> styles = new java.util.ArrayList<>();
            styles.add(new LabelStyleOption(null, "Default"));
            for (String style : session.textFieldStyleNames(
                    node.textField.fontAssetId != null)) {
                styles.add(new LabelStyleOption(style, style));
            }
            addDirectChoice("Style", "hudTextFieldStyle", nodeId,
                    nodeValue(nodeId, current -> labelStyleOption(current.textField.styleName)),
                    styles.toArray(new LabelStyleOption[0]), LabelStyleOption::label,
                    selected -> session.editSelectedTextFieldStyle(selected.styleName()));
            addFontChoice(nodeId, "hudTextFieldFont");
            addNonNegativeInteger("Max length (0 = unlimited)", "hudTextFieldMaxLength", nodeId,
                    nodeValue(nodeId, current -> current.textField.maxLength),
                    value -> edit(nodeId,
                            (current, ignored) -> current.textField.maxLength = value));
            addCheck("Password", "hudTextFieldPassword", nodeId,
                    nodeValue(nodeId, current -> current.textField.passwordMode),
                    (current, ignored, checked) -> current.textField.passwordMode = checked);
        }
        if (node.selectBox != null) addSelectBoxProperties(nodeId, node);
        if (node.list != null) addListProperties(nodeId, node);
        if (node.checkBox != null) addCheckBoxProperties(nodeId, node);
        if (node.slider != null) addSliderProperties(nodeId, node);
        if (node.progressBar != null) addProgressBarProperties(nodeId, node);
        if (node.scrollPane != null) addScrollPaneProperties(nodeId, node);
        if (node.window != null || node.dialog != null) addWindowProperties(nodeId, node);
        if ((node.kind == HudNodeKind.TEXT_BUTTON || node.kind == HudNodeKind.IMAGE_BUTTON
                || node.kind == HudNodeKind.IMAGE_TEXT_BUTTON)
                && HudLayoutAuthoring.resultButtonOwner(session.document(), nodeId) == null)
            addWindowActions(nodeId, node);
        if (node.image != null) {
            addReadOnly("Image source", String.valueOf(node.image.source));
            addReadOnly("Resource", node.image.resourceName);
        }
        if (node.container != null) {
            addCheck("Clip", "hudClip", nodeId, nodeValue(nodeId, current -> current.container.clip),
                    (current, ignored, checked) -> current.container.clip = checked);
        }
    }

    private void addScrollPaneProperties(String nodeId, HudNode node) {
        String screenId = session.screenId();
        List<LabelStyleOption> styles = new java.util.ArrayList<>();
        styles.add(new LabelStyleOption(null, "Default"));
        for (String style : session.scrollPaneStyleNames()) styles.add(new LabelStyleOption(style, style));
        addDirectChoice("Style", "hudScrollPaneStyle", nodeId,
                nodeValue(nodeId, current -> labelStyleOption(current.scrollPane.styleName)),
                styles.toArray(new LabelStyleOption[0]), LabelStyleOption::label,
                selected -> applyScrollPaneEdit(screenId, nodeId,
                        () -> session.editSelectedScrollPaneStyle(selected.styleName())));
        addDirectCheck("Disable horizontal scrolling", "hudScrollPaneDisabledX", nodeId,
                nodeValue(nodeId, current -> current.scrollPane.scrollingDisabledX),
                checked -> applyScrollPaneEdit(screenId, nodeId,
                        () -> session.editSelectedScrollPaneScrollingDisabledX(checked)));
        addDirectCheck("Disable vertical scrolling", "hudScrollPaneDisabledY", nodeId,
                nodeValue(nodeId, current -> current.scrollPane.scrollingDisabledY),
                checked -> applyScrollPaneEdit(screenId, nodeId,
                        () -> session.editSelectedScrollPaneScrollingDisabledY(checked)));
        addDirectCheck("Fade scroll bars", "hudScrollPaneFade", nodeId,
                nodeValue(nodeId, current -> current.scrollPane.fadeScrollBars),
                checked -> applyScrollPaneEdit(screenId, nodeId,
                        () -> session.editSelectedScrollPaneFadeScrollBars(checked)));
        addDirectCheck("Flick scroll", "hudScrollPaneFlick", nodeId,
                nodeValue(nodeId, current -> current.scrollPane.flickScroll),
                checked -> applyScrollPaneEdit(screenId, nodeId,
                        () -> session.editSelectedScrollPaneFlickScroll(checked)));
        addDirectCheck("Smooth scrolling", "hudScrollPaneSmooth", nodeId,
                nodeValue(nodeId, current -> current.scrollPane.smoothScrolling),
                checked -> applyScrollPaneEdit(screenId, nodeId,
                        () -> session.editSelectedScrollPaneSmoothScrolling(checked)));
        addDirectCheck("Overscroll horizontally", "hudScrollPaneOverscrollX", nodeId,
                nodeValue(nodeId, current -> current.scrollPane.overscrollX),
                checked -> applyScrollPaneEdit(screenId, nodeId,
                        () -> session.editSelectedScrollPaneOverscrollX(checked)));
        addDirectCheck("Overscroll vertically", "hudScrollPaneOverscrollY", nodeId,
                nodeValue(nodeId, current -> current.scrollPane.overscrollY),
                checked -> applyScrollPaneEdit(screenId, nodeId,
                        () -> session.editSelectedScrollPaneOverscrollY(checked)));
        if (hasScrollPaneDiagnostic(screenId, nodeId)) {
            VisLabel diagnostic = new VisLabel(scrollPaneDiagnostic);
            diagnostic.setName("hudScrollPaneError");
            diagnostic.setWrap(true);
            diagnostic.setColor(1f, .55f, .55f, 1f);
            add(diagnostic).growX().colspan(2).padTop(2f).row();
        }
    }

    private void addWindowProperties(String nodeId, HudNode node) {
        String screenId = session.screenId();
        HudWindowData authoredWindow = windowData(node);
        addText("Title", "hudWindowTitle", nodeId,
                nodeValue(nodeId, current -> windowData(current).title),
                value -> applyWindowEdit(screenId, nodeId, "Edit HUD Window title",
                        data -> data.title = value));

        List<LabelStyleOption> styles = new java.util.ArrayList<>();
        styles.add(new LabelStyleOption(null, "Default"));
        for (String name : session.windowStyleNames(authoredWindow.fontAssetId != null)) {
            styles.add(new LabelStyleOption(name, name));
        }
        if (authoredWindow.styleName != null && !authoredWindow.styleName.isBlank()
                && styles.stream().noneMatch(option -> authoredWindow.styleName.equals(option.styleName()))) {
            styles.add(new LabelStyleOption(authoredWindow.styleName, authoredWindow.styleName));
        }
        addDirectChoice("Style", "hudWindowStyle", nodeId,
                nodeValue(nodeId, current -> labelStyleOption(windowData(current).styleName)),
                styles.toArray(new LabelStyleOption[0]), LabelStyleOption::label,
                selected -> applyWindowEdit(screenId, nodeId, "Edit HUD Window style",
                        data -> data.styleName = selected.styleName()));

        List<HudEditorSession.FontResourceOption> fonts = new java.util.ArrayList<>();
        fonts.add(new HudEditorSession.FontResourceOption(null, "Style font"));
        fonts.addAll(session.fontResourceOptions());
        Integer authoredFontId = authoredWindow.fontAssetId;
        if (authoredFontId != null && fonts.stream().noneMatch(option ->
                Objects.equals(option.assetId(), authoredFontId))) {
            fonts.add(new HudEditorSession.FontResourceOption(authoredFontId,
                    "Missing: " + authoredFontId));
        }
        VisSelectBox<Choice<HudEditorSession.FontResourceOption>> fontBox = new VisSelectBox<>();
        fontBox.setName("hudWindowFont");
        @SuppressWarnings("unchecked") Choice<HudEditorSession.FontResourceOption>[] fontChoices =
                new Choice[fonts.size()];
        for (int i = 0; i < fonts.size(); i++) {
            fontChoices[i] = new Choice<>(fonts.get(i), fonts.get(i).label());
        }
        fontBox.setItems(fontChoices);
        for (Choice<HudEditorSession.FontResourceOption> option : fontChoices) {
            if (Objects.equals(option.value.assetId(), authoredFontId)) fontBox.setSelected(option);
        }
        fontBox.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (refreshing || !isCurrentTarget(screenId, nodeId)) return;
                Choice<HudEditorSession.FontResourceOption> selected = fontBox.getSelected();
                if (selected != null) applyWindowEdit(screenId, nodeId, "Edit HUD Window title font",
                        data -> data.fontAssetId = selected.value.assetId());
            }
        });
        addFormLabel("Font");
        add(fontBox).growX().left().row();

        addDirectCheck("Movable", "hudWindowMovable", nodeId,
                nodeValue(nodeId, current -> windowData(current).movable),
                checked -> applyWindowEdit(screenId, nodeId, "Edit HUD Window movable",
                        data -> data.movable = checked));
        addDirectCheck("Resizable", "hudWindowResizable", nodeId,
                nodeValue(nodeId, current -> windowData(current).resizable),
                checked -> applyWindowEdit(screenId, nodeId, "Edit HUD Window resizable",
                        data -> data.resizable = checked));
        addDirectCheck("Modal", "hudWindowModal", nodeId,
                nodeValue(nodeId, current -> windowData(current).modal),
                checked -> applyWindowEdit(screenId, nodeId, "Edit HUD Window modal",
                        data -> data.modal = checked));
        addDirectCheck("Keep within stage", "hudWindowKeepWithinStage", nodeId,
                nodeValue(nodeId, current -> windowData(current).keepWithinStage),
                checked -> applyWindowEdit(screenId, nodeId, "Edit HUD Window stage bounds",
                        data -> data.keepWithinStage = checked));
        if (windowDiagnostic != null && Objects.equals(windowDiagnosticScreenId, screenId)
                && Objects.equals(windowDiagnosticNodeId, nodeId)) {
            VisLabel diagnostic = new VisLabel(windowDiagnostic);
            diagnostic.setName("hudWindowError");
            diagnostic.setWrap(true);
            diagnostic.setColor(1f, .55f, .55f, 1f);
            add(diagnostic).growX().colspan(2).padTop(2f).row();
        }
    }

    private static HudWindowData windowData(HudNode node) {
        return node.window != null ? node.window : node.dialog;
    }

    private void addWindowActions(String nodeId, HudNode node) {
        List<String> targets = new java.util.ArrayList<>();
        collectWindowTargets(session.document().root, targets);
        if (targets.isEmpty()) return;
        addPropertySeparator();
        add(new VisLabel("Window / Dialog actions")).left().colspan(2).row();
        for (int i = 0; i < node.windowActions.size(); i++) {
            final int index = i;
            HudWindowAction association = node.windowActions.get(i);
            List<String> choices = new java.util.ArrayList<>();
            for (String target : targets) {
                if (target.equals(association.targetId) || node.windowActions.stream()
                        .noneMatch(other -> other != association && target.equals(other.targetId))) {
                    choices.add(target);
                }
            }
            addDirectChoice("Target", "hudWindowActionTarget" + i, nodeId,
                    nodeValue(nodeId, current -> current.windowActions.get(index).targetId),
                    choices.toArray(new String[0]), target -> target,
                    selected -> edit(nodeId, (current, ignored) ->
                            current.windowActions.get(index).targetId = selected));
            addDirectChoice("Action", "hudWindowActionKind" + i, nodeId,
                    nodeValue(nodeId, current -> current.windowActions.get(index).action),
                    HudWindowActionKind.values(), HudInspectorView::enumLabel,
                    selected -> edit(nodeId, (current, ignored) ->
                            current.windowActions.get(index).action = selected));
            VisTextButton remove = new VisTextButton("Remove action");
            remove.setName("hudRemoveWindowAction" + i);
            remove.addListener(change(() -> edit(nodeId, (current, ignored) ->
                    current.windowActions.remove(index))));
            addFormLabel("");
            add(remove).left().row();
        }
        String available = targets.stream().filter(target -> node.windowActions.stream()
                .noneMatch(action -> target.equals(action.targetId))).findFirst().orElse(null);
        if (available != null) {
            VisTextButton addAction = new VisTextButton("Add action");
            addAction.setName("hudAddWindowAction");
            addAction.addListener(change(() -> edit(nodeId, (current, ignored) ->
                    current.windowActions.add(new HudWindowAction(available, HudWindowActionKind.SHOW)))));
            addFormLabel("");
            add(addAction).left().row();
        }
    }

    private static void collectWindowTargets(HudNode node, List<String> targets) {
        if (node == null) return;
        if (node.kind == HudNodeKind.WINDOW || node.kind == HudNodeKind.DIALOG) {
            targets.add(node.id);
        }
        if (node.children != null) {
            for (HudChild child : node.children) {
                if (child != null) collectWindowTargets(child.node, targets);
            }
        }
    }

    private void applyWindowEdit(String screenId, String nodeId, String label,
                                 java.util.function.Consumer<games.pixscape.runtime.hud.document.HudWindowData> mutation) {
        if (!isCurrentTarget(screenId, nodeId)) return;
        windowDiagnostic = null;
        try {
            if (!session.editWindow(screenId, nodeId, label, mutation)) rebuild();
        } catch (HudEditRejectedException rejected) {
            windowDiagnosticScreenId = screenId;
            windowDiagnosticNodeId = nodeId;
            windowDiagnostic = rejected.getMessage();
            rebuild();
        }
    }

    private void clearStaleWindowDiagnostic(HudNode node) {
        if (windowDiagnostic == null) return;
        if (!Objects.equals(windowDiagnosticScreenId, session.screenId())
                || node == null || !Objects.equals(windowDiagnosticNodeId, node.id)) {
            windowDiagnostic = null;
        }
    }

    private void applyScrollPaneEdit(String screenId, String nodeId, Runnable edit) {
        if (!isCurrentTarget(screenId, nodeId)) return;
        clearScrollPaneDiagnostic(screenId, nodeId);
        try {
            edit.run();
        } catch (HudEditRejectedException rejected) {
            scrollPaneDiagnosticScreenId = screenId;
            scrollPaneDiagnosticNodeId = nodeId;
            scrollPaneDiagnostic = rejected.getMessage();
            rebuild();
        }
    }

    private void clearScrollPaneDiagnostic(String screenId, String nodeId) {
        if (Objects.equals(scrollPaneDiagnosticScreenId, screenId)
                && Objects.equals(scrollPaneDiagnosticNodeId, nodeId)) scrollPaneDiagnostic = null;
    }

    private boolean hasScrollPaneDiagnostic(String screenId, String nodeId) {
        return Objects.equals(scrollPaneDiagnosticScreenId, screenId)
                && Objects.equals(scrollPaneDiagnosticNodeId, nodeId)
                && scrollPaneDiagnostic != null && !scrollPaneDiagnostic.isBlank();
    }

    private void clearStaleScrollPaneDiagnostic(HudNode node) {
        if (scrollPaneDiagnostic == null) return;
        if (!Objects.equals(scrollPaneDiagnosticScreenId, session.screenId())
                || node == null || !Objects.equals(scrollPaneDiagnosticNodeId, node.id)) {
            scrollPaneDiagnosticScreenId = null;
            scrollPaneDiagnosticNodeId = null;
            scrollPaneDiagnostic = null;
        }
    }

    private void addFontChoice(String nodeId, String controlName) {
        List<HudEditorSession.FontResourceOption> options = new java.util.ArrayList<>();
        options.add(new HudEditorSession.FontResourceOption(null, "Style font"));
        options.addAll(session.fontResourceOptions());
        VisSelectBox<Choice<HudEditorSession.FontResourceOption>> box = new VisSelectBox<>();
        box.setName(controlName);
        @SuppressWarnings("unchecked") Choice<HudEditorSession.FontResourceOption>[] choices =
                new Choice[options.size()];
        Integer currentId = nodeValue(nodeId, HudFontReferences::assetId).get();
        for (int index = 0; index < options.size(); index++) {
            HudEditorSession.FontResourceOption option = options.get(index);
            choices[index] = new Choice<>(option, option.label());
        }
        box.setItems(choices);
        for (Choice<HudEditorSession.FontResourceOption> choice : choices) {
            if (Objects.equals(choice.value.assetId(), currentId)) box.setSelected(choice);
        }
        String screenId = session.screenId();
        box.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (refreshing || !isCurrentTarget(screenId, nodeId)) return;
                Choice<HudEditorSession.FontResourceOption> selected = box.getSelected();
                if (selected == null) return;
                clearFontDiagnostic(screenId, nodeId);
                try {
                    session.assignFont(screenId, nodeId, selected.value.assetId());
                } catch (HudEditRejectedException rejected) {
                    setFontDiagnostic(screenId, nodeId, rejected.getMessage());
                    rebuild();
                }
            }
        });
        VisTable controls = new VisTable();
        controls.defaults().left().padRight(PROPERTY_CELL_PAD);
        controls.add(box).growX();
        addFormLabel("Font");
        add(controls).growX().left().row();
        if (hasFontDiagnostic(screenId, nodeId)) {
            VisLabel diagnostic = new VisLabel(fontDiagnostic);
            diagnostic.setName(controlName + "Error");
            diagnostic.setWrap(true);
            diagnostic.setColor(1f, .55f, .55f, 1f);
            add(diagnostic).growX().colspan(2).padTop(2f).row();
        }
    }

    private void setFontDiagnostic(String screenId, String nodeId, String diagnostic) {
        fontDiagnosticScreenId = screenId;
        fontDiagnosticNodeId = nodeId;
        fontDiagnostic = diagnostic;
    }

    private void clearFontDiagnostic(String screenId, String nodeId) {
        if (Objects.equals(fontDiagnosticScreenId, screenId)
                && Objects.equals(fontDiagnosticNodeId, nodeId)) {
            fontDiagnostic = null;
        }
    }

    private boolean hasFontDiagnostic(String screenId, String nodeId) {
        return Objects.equals(fontDiagnosticScreenId, screenId)
                && Objects.equals(fontDiagnosticNodeId, nodeId)
                && fontDiagnostic != null && !fontDiagnostic.isBlank();
    }

    private void clearStaleFontDiagnostic(HudNode node) {
        if (fontDiagnostic == null) return;
        if (!Objects.equals(fontDiagnosticScreenId, session.screenId())
                || node == null || !Objects.equals(fontDiagnosticNodeId, node.id)) {
            fontDiagnosticScreenId = null;
            fontDiagnosticNodeId = null;
            fontDiagnostic = null;
        }
    }

    private void addSelectBoxProperties(String nodeId, HudNode node) {
        String screenId = session.screenId();
        int activeIndex = selectBoxItemsEditorState.resolve(screenId, nodeId, node.selectBox.items);
        VisList<String> items = new VisList<String>();
        items.setName("hudSelectBoxItems");
        items.setItems(node.selectBox.items.toArray(new String[node.selectBox.items.size()]));
        if (activeIndex >= 0) items.setSelectedIndex(activeIndex);
        SimpleTextField value = new SimpleTextField();
        value.setName("hudSelectBoxItemText");
        value.setDisabled(activeIndex < 0);
        value.bind(() -> selectBoxItem(nodeId),
                text -> renameSelectBoxItem(screenId, nodeId, text));
        installTextLifecycle(value);
        items.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                value.commit();
                String selected = items.getSelected();
                HudNode current = selectBoxNode(nodeId);
                int index = current == null || current.selectBox == null || selected == null
                        ? -1 : current.selectBox.items.indexOf(selected);
                selectBoxItemsEditorState.activate(screenId, nodeId, selected, index);
                value.refresh();
            }
        });
        items.addListener(new InputListener() {
            @Override public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.DEL || keycode == Input.Keys.FORWARD_DEL) {
                    event.stop();
                    return true;
                }
                return false;
            }
        });
        VisTable editor = new VisTable();
        editor.defaults().left().top().pad(PROPERTY_CELL_PAD);
        VisScrollPane itemScroll = new VisScrollPane(items);
        itemScroll.setFadeScrollBars(false);
        editor.add(itemScroll).minSize(120f, 72f).growX().row();
        editor.add(value).growX().row();
        HorizontalGroup actions = new HorizontalGroup().space(4f);
        VisTextButton add = new VisTextButton("+");
        VisTextButton remove = new VisTextButton("-");
        VisTextButton up = new VisTextButton("Up");
        VisTextButton down = new VisTextButton("Down");
        add.setName("hudSelectBoxAddItem");
        remove.setName("hudSelectBoxRemoveItem");
        up.setName("hudSelectBoxMoveItemUp");
        down.setName("hudSelectBoxMoveItemDown");
        remove.setDisabled(activeIndex < 0);
        up.setDisabled(activeIndex <= 0);
        down.setDisabled(activeIndex < 0 || activeIndex + 1 >= node.selectBox.items.size());
        actions.addActor(add); actions.addActor(remove); actions.addActor(up); actions.addActor(down);
        editor.add(actions).left().row();
        add.addListener(change(() -> addSelectBoxItem(screenId, nodeId)));
        remove.addListener(change(() -> removeSelectBoxItem(screenId, nodeId)));
        up.addListener(change(() -> moveSelectBoxItem(screenId, nodeId, -1)));
        down.addListener(change(() -> moveSelectBoxItem(screenId, nodeId, 1)));
        addFormLabel("Items").top();
        add(editor).growX().left().row();
        if (selectBoxItemsEditorState.hasDiagnostic(screenId, nodeId)) {
            VisLabel diagnostic = new VisLabel(selectBoxItemsEditorState.diagnostic());
            diagnostic.setName("hudSelectBoxItemError");
            diagnostic.setWrap(true);
            diagnostic.setColor(1f, .55f, .55f, 1f);
            add(diagnostic).growX().colspan(2).padTop(2f).row();
        }

        VisSelectBox<String> selected = new VisSelectBox<String>();
        selected.setName("hudSelectBoxSelected");
        selected.setItems(node.selectBox.items.toArray(new String[node.selectBox.items.size()]));
        if (node.selectBox.selectedIndex >= 0) selected.setSelectedIndex(node.selectBox.selectedIndex);
        selected.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (refreshing || !isCurrentTarget(nodeId)) return;
                int index = node.selectBox.items.indexOf(selected.getSelected());
                edit(nodeId, (current, ignored) -> current.selectBox.selectedIndex = index);
            }
        });
        addFormLabel("Selected");
        add(selected).growX().left().row();
        List<LabelStyleOption> styles = new java.util.ArrayList<>();
        styles.add(new LabelStyleOption(null, "Default"));
        for (String style : session.selectBoxStyleNames(node.selectBox.fontAssetId != null)) {
            styles.add(new LabelStyleOption(style, style));
        }
        addDirectChoice("Style", "hudSelectBoxStyle", nodeId,
                nodeValue(nodeId, current -> labelStyleOption(current.selectBox.styleName)),
                styles.toArray(new LabelStyleOption[0]), LabelStyleOption::label,
                option -> session.editSelectedSelectBoxStyle(option.styleName()));
        addFontChoice(nodeId, "hudSelectBoxFont");
        addNonNegativeInteger("Max visible items (0 = unlimited)", "hudSelectBoxMaxListCount", nodeId,
                nodeValue(nodeId, current -> current.selectBox.maxListCount),
                number -> edit(nodeId, (current, ignored) -> current.selectBox.maxListCount = number));
        addCheck("Disabled", "hudSelectBoxDisabled", nodeId,
                nodeValue(nodeId, current -> current.selectBox.disabled),
                (current, ignored, checked) -> current.selectBox.disabled = checked);
    }

    private void addListProperties(String nodeId, HudNode node) {
        String screenId = session.screenId();
        if (!Objects.equals(listEditorNodeId, nodeId)
                || !Objects.equals(listEditorScreenId, screenId)) {
            listEditorNodeId = nodeId;
            listEditorScreenId = screenId;
            listEditorIndex = 0;
        }
        java.util.List<String> values = node.list.items;
        listEditorIndex = values.isEmpty() ? -1 : Math.max(0, Math.min(listEditorIndex, values.size() - 1));
        String[] labels = new String[values.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = (i + 1) + ": " + values.get(i);
        VisList<String> items = new VisList<>();
        items.setName("hudListItems");
        items.setItems(labels);
        if (listEditorIndex >= 0) items.setSelectedIndex(listEditorIndex);
        SimpleTextField value = new SimpleTextField();
        value.setName("hudListItemText");
        value.setDisabled(listEditorIndex < 0);
        value.bind(() -> {
            HudNode current = selectBoxNode(nodeId);
            return current == null || current.list == null || listEditorIndex < 0
                    || listEditorIndex >= current.list.items.size() ? "" : current.list.items.get(listEditorIndex);
        }, text -> {
            int index = listEditorIndex;
            if (text != null && isCurrentTarget(screenId, nodeId) && index >= 0) {
                session.editSelectedNode("Rename HUD List item", (current, ignored) ->
                        current.list.items.set(index, text));
            }
        });
        installTextLifecycle(value);
        items.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                value.commit();
                listEditorIndex = items.getSelectedIndex();
                value.refresh();
            }
        });
        items.addListener(new InputListener() {
            @Override public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.DEL || keycode == Input.Keys.FORWARD_DEL) {
                    event.stop();
                    return true;
                }
                return false;
            }
        });
        VisTable editor = new VisTable();
        editor.defaults().left().top().pad(PROPERTY_CELL_PAD);
        VisScrollPane itemScroll = new VisScrollPane(items);
        itemScroll.setFadeScrollBars(false);
        editor.add(itemScroll).minSize(120f, 72f).growX().row();
        editor.add(value).growX().row();
        HorizontalGroup actions = new HorizontalGroup().space(4f);
        VisTextButton add = new VisTextButton("+");
        VisTextButton remove = new VisTextButton("-");
        VisTextButton up = new VisTextButton("Up");
        VisTextButton down = new VisTextButton("Down");
        add.setName("hudListAddItem");
        remove.setName("hudListRemoveItem");
        up.setName("hudListMoveItemUp");
        down.setName("hudListMoveItemDown");
        remove.setDisabled(listEditorIndex < 0);
        up.setDisabled(listEditorIndex <= 0);
        down.setDisabled(listEditorIndex < 0 || listEditorIndex + 1 >= values.size());
        actions.addActor(add); actions.addActor(remove); actions.addActor(up); actions.addActor(down);
        editor.add(actions).left().row();
        add.addListener(change(() -> {
            if (!isCurrentTarget(screenId, nodeId)) return;
            session.editSelectedNode("Add HUD List item", (current, ignored) -> {
                current.list.items.add(nextItemName(current.list.items));
                if (current.list.required && current.list.selectedIndex == -1) current.list.selectedIndex = 0;
            });
            listEditorIndex = values.size();
            rebuild();
        }));
        remove.addListener(change(() -> {
            int index = listEditorIndex;
            if (!isCurrentTarget(screenId, nodeId) || index < 0) return;
            session.editSelectedNode("Remove HUD List item", (current, ignored) -> {
                current.list.items.remove(index);
                if (current.list.items.isEmpty()) current.list.selectedIndex = -1;
                else if (current.list.selectedIndex > index) current.list.selectedIndex--;
                else if (current.list.selectedIndex == index)
                    current.list.selectedIndex = Math.min(index, current.list.items.size() - 1);
            });
            listEditorIndex = Math.min(index, values.size() - 2);
            rebuild();
        }));
        up.addListener(change(() -> moveListItem(screenId, nodeId, -1)));
        down.addListener(change(() -> moveListItem(screenId, nodeId, 1)));
        addFormLabel("Items").top();
        add(editor).growX().left().row();

        VisSelectBox<String> selected = new VisSelectBox<>();
        selected.setName("hudListSelected");
        String[] choices = new String[labels.length + (node.list.required && !values.isEmpty() ? 0 : 1)];
        int offset = choices.length - labels.length;
        if (offset == 1) choices[0] = "Aucune";
        System.arraycopy(labels, 0, choices, offset, labels.length);
        selected.setItems(choices);
        selected.setSelectedIndex(node.list.selectedIndex + offset);
        selected.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (refreshing || !isCurrentTarget(screenId, nodeId)) return;
                int index = selected.getSelectedIndex() - offset;
                edit(nodeId, (current, ignored) -> current.list.selectedIndex = index);
            }
        });
        addFormLabel("Selected");
        add(selected).growX().left().row();
        VisCheckBox required = new VisCheckBox("Required");
        required.setName("hudListRequired");
        required.setChecked(node.list.required);
        required.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (refreshing || !isCurrentTarget(screenId, nodeId)) return;
                edit(nodeId, (current, ignored) -> {
                    current.list.required = required.isChecked();
                    if (current.list.required && current.list.selectedIndex == -1
                            && !current.list.items.isEmpty()) current.list.selectedIndex = 0;
                });
                rebuild();
            }
        });
        add(required).colspan(2).left().row();
        java.util.List<LabelStyleOption> styles = new java.util.ArrayList<>();
        styles.add(new LabelStyleOption(null, "Default"));
        for (String style : session.listStyleNames(node.list.fontAssetId != null))
            styles.add(new LabelStyleOption(style, style));
        addDirectChoice("Style", "hudListStyle", nodeId,
                nodeValue(nodeId, current -> labelStyleOption(current.list.styleName)),
                styles.toArray(new LabelStyleOption[0]), LabelStyleOption::label,
                option -> session.editSelectedListStyle(option.styleName()));
        addFontChoice(nodeId, "hudListFont");
    }

    private void moveListItem(String screenId, String nodeId, int direction) {
        int from = listEditorIndex;
        int to = from + direction;
        HudNode current = selectBoxNode(nodeId);
        if (!isCurrentTarget(screenId, nodeId) || current == null || current.list == null
                || from < 0 || to < 0 || to >= current.list.items.size()) return;
        session.editSelectedNode("Move HUD List item", (node, ignored) -> {
            String moved = node.list.items.remove(from);
            node.list.items.add(to, moved);
            if (node.list.selectedIndex == from) node.list.selectedIndex = to;
            else if (from < node.list.selectedIndex && to >= node.list.selectedIndex)
                node.list.selectedIndex--;
            else if (from > node.list.selectedIndex && to <= node.list.selectedIndex)
                node.list.selectedIndex++;
        });
        listEditorIndex = to;
        rebuild();
    }

    private void addCheckBoxProperties(String nodeId, HudNode node) {
        addMultilineText("Text", "hudCheckBoxText", nodeId,
                nodeValue(nodeId, current -> current.checkBox.text),
                value -> { if (isCurrentTarget(nodeId)) session.editSelectedCheckBoxText(value); });
        List<LabelStyleOption> styles = new java.util.ArrayList<>();
        styles.add(new LabelStyleOption(null, "Default"));
        for (String style : session.checkBoxStyleNames(node.checkBox.fontAssetId != null)) {
            styles.add(new LabelStyleOption(style, style));
        }
        addDirectChoice("Style", "hudCheckBoxStyle", nodeId,
                nodeValue(nodeId, current -> labelStyleOption(current.checkBox.styleName)),
                styles.toArray(new LabelStyleOption[0]), LabelStyleOption::label,
                selected -> session.editSelectedCheckBoxStyle(selected.styleName()));
        addFontChoice(nodeId, "hudCheckBoxFont");
        addCheck("Checked", "hudCheckBoxChecked", nodeId,
                nodeValue(nodeId, current -> current.checkBox.checked),
                (current, ignored, checked) -> current.checkBox.checked = checked);
        addCheck("Disabled", "hudCheckBoxDisabled", nodeId,
                nodeValue(nodeId, current -> current.checkBox.disabled),
                (current, ignored, checked) -> current.checkBox.disabled = checked);
    }

    private void addSliderProperties(String nodeId, HudNode node) {
        String screenId = session.screenId();
        addDirectChoice("Orientation", "hudSliderOrientation", nodeId,
                nodeValue(nodeId, current -> current.slider.orientation),
                HudSliderOrientation.values(), HudInspectorView::enumLabel,
                selected -> applySliderEdit(screenId, nodeId,
                        () -> session.editSelectedSliderOrientation(selected)));
        addExactNumber("Min", "hudSliderMin", nodeId,
                nodeValue(nodeId, current -> current.slider.min),
                value -> applySliderEdit(screenId, nodeId,
                        () -> session.editSelectedSliderMin(value)),
                Float::isFinite);
        addExactNumber("Max", "hudSliderMax", nodeId,
                nodeValue(nodeId, current -> current.slider.max),
                value -> applySliderEdit(screenId, nodeId,
                        () -> session.editSelectedSliderMax(value)),
                Float::isFinite);
        addExactNumber("Step", "hudSliderStep", nodeId,
                nodeValue(nodeId, current -> current.slider.stepSize),
                value -> applySliderEdit(screenId, nodeId,
                        () -> session.editSelectedSliderStepSize(value)),
                value -> Float.isFinite(value) && value > 0f);
        addExactNumber("Value", "hudSliderValue", nodeId,
                nodeValue(nodeId, current -> current.slider.value),
                value -> applySliderEdit(screenId, nodeId,
                        () -> session.editSelectedSliderValue(value)),
                Float::isFinite);
        List<LabelStyleOption> styles = new java.util.ArrayList<>();
        styles.add(new LabelStyleOption(null, "Default"));
        for (String style : session.sliderStyleNames()) {
            styles.add(new LabelStyleOption(style, style));
        }
        addDirectChoice("Style", "hudSliderStyle", nodeId,
                nodeValue(nodeId, current -> labelStyleOption(current.slider.styleName)),
                styles.toArray(new LabelStyleOption[0]), LabelStyleOption::label,
                selected -> applySliderEdit(screenId, nodeId,
                        () -> session.editSelectedSliderStyle(selected.styleName())));
        addDirectCheck("Disabled", "hudSliderDisabled", nodeId,
                nodeValue(nodeId, current -> current.slider.disabled),
                checked -> applySliderEdit(screenId, nodeId,
                        () -> session.editSelectedSliderDisabled(checked)));
        if (hasSliderDiagnostic(screenId, nodeId)) {
            VisLabel diagnostic = new VisLabel(sliderDiagnostic);
            diagnostic.setName("hudSliderError");
            diagnostic.setWrap(true);
            diagnostic.setColor(1f, .55f, .55f, 1f);
            add(diagnostic).growX().colspan(2).padTop(2f).row();
        }
    }

    private void applySliderEdit(String screenId, String nodeId, Runnable edit) {
        if (!isCurrentTarget(screenId, nodeId)) return;
        clearSliderDiagnostic(screenId, nodeId);
        try {
            edit.run();
        } catch (HudEditRejectedException rejected) {
            sliderDiagnosticScreenId = screenId;
            sliderDiagnosticNodeId = nodeId;
            sliderDiagnostic = rejected.getMessage();
            rebuild();
        }
    }

    private void clearSliderDiagnostic(String screenId, String nodeId) {
        if (Objects.equals(sliderDiagnosticScreenId, screenId)
                && Objects.equals(sliderDiagnosticNodeId, nodeId)) {
            sliderDiagnostic = null;
        }
    }

    private boolean hasSliderDiagnostic(String screenId, String nodeId) {
        return Objects.equals(sliderDiagnosticScreenId, screenId)
                && Objects.equals(sliderDiagnosticNodeId, nodeId)
                && sliderDiagnostic != null && !sliderDiagnostic.isBlank();
    }

    private void clearStaleSliderDiagnostic(HudNode node) {
        if (sliderDiagnostic == null) return;
        if (!Objects.equals(sliderDiagnosticScreenId, session.screenId())
                || node == null || !Objects.equals(sliderDiagnosticNodeId, node.id)) {
            sliderDiagnosticScreenId = null;
            sliderDiagnosticNodeId = null;
            sliderDiagnostic = null;
        }
    }

    private void addProgressBarProperties(String nodeId, HudNode node) {
        String screenId = session.screenId();
        addDirectChoice("Orientation", "hudProgressBarOrientation", nodeId,
                nodeValue(nodeId, current -> current.progressBar.orientation),
                HudSliderOrientation.values(), HudInspectorView::enumLabel,
                selected -> applyProgressBarEdit(screenId, nodeId,
                        () -> session.editSelectedProgressBarOrientation(selected)));
        addExactNumber("Min", "hudProgressBarMin", nodeId,
                nodeValue(nodeId, current -> current.progressBar.min),
                value -> applyProgressBarEdit(screenId, nodeId,
                        () -> session.editSelectedProgressBarMin(value)), Float::isFinite);
        addExactNumber("Max", "hudProgressBarMax", nodeId,
                nodeValue(nodeId, current -> current.progressBar.max),
                value -> applyProgressBarEdit(screenId, nodeId,
                        () -> session.editSelectedProgressBarMax(value)), Float::isFinite);
        addExactNumber("Step", "hudProgressBarStep", nodeId,
                nodeValue(nodeId, current -> current.progressBar.stepSize),
                value -> applyProgressBarEdit(screenId, nodeId,
                        () -> session.editSelectedProgressBarStepSize(value)),
                value -> Float.isFinite(value) && value > 0f);
        addExactNumber("Value", "hudProgressBarValue", nodeId,
                nodeValue(nodeId, current -> current.progressBar.value),
                value -> applyProgressBarEdit(screenId, nodeId,
                        () -> session.editSelectedProgressBarValue(value)), Float::isFinite);
        List<LabelStyleOption> styles = new java.util.ArrayList<>();
        styles.add(new LabelStyleOption(null, "Default"));
        for (String style : session.progressBarStyleNames()) styles.add(new LabelStyleOption(style, style));
        addDirectChoice("Style", "hudProgressBarStyle", nodeId,
                nodeValue(nodeId, current -> labelStyleOption(current.progressBar.styleName)),
                styles.toArray(new LabelStyleOption[0]), LabelStyleOption::label,
                selected -> applyProgressBarEdit(screenId, nodeId,
                        () -> session.editSelectedProgressBarStyle(selected.styleName())));
        addDirectCheck("Disabled", "hudProgressBarDisabled", nodeId,
                nodeValue(nodeId, current -> current.progressBar.disabled),
                checked -> applyProgressBarEdit(screenId, nodeId,
                        () -> session.editSelectedProgressBarDisabled(checked)));
        if (hasProgressBarDiagnostic(screenId, nodeId)) {
            VisLabel diagnostic = new VisLabel(progressBarDiagnostic);
            diagnostic.setName("hudProgressBarError");
            diagnostic.setWrap(true);
            diagnostic.setColor(1f, .55f, .55f, 1f);
            add(diagnostic).growX().colspan(2).padTop(2f).row();
        }
    }

    private void applyProgressBarEdit(String screenId, String nodeId, Runnable edit) {
        if (!isCurrentTarget(screenId, nodeId)) return;
        clearProgressBarDiagnostic(screenId, nodeId);
        try {
            edit.run();
        } catch (HudEditRejectedException rejected) {
            progressBarDiagnosticScreenId = screenId;
            progressBarDiagnosticNodeId = nodeId;
            progressBarDiagnostic = rejected.getMessage();
            rebuild();
        }
    }

    private void clearProgressBarDiagnostic(String screenId, String nodeId) {
        if (Objects.equals(progressBarDiagnosticScreenId, screenId)
                && Objects.equals(progressBarDiagnosticNodeId, nodeId)) progressBarDiagnostic = null;
    }

    private boolean hasProgressBarDiagnostic(String screenId, String nodeId) {
        return Objects.equals(progressBarDiagnosticScreenId, screenId)
                && Objects.equals(progressBarDiagnosticNodeId, nodeId)
                && progressBarDiagnostic != null && !progressBarDiagnostic.isBlank();
    }

    private void clearStaleProgressBarDiagnostic(HudNode node) {
        if (progressBarDiagnostic == null) return;
        if (!Objects.equals(progressBarDiagnosticScreenId, session.screenId())
                || node == null || !Objects.equals(progressBarDiagnosticNodeId, node.id)) {
            progressBarDiagnosticScreenId = null;
            progressBarDiagnosticNodeId = null;
            progressBarDiagnostic = null;
        }
    }

    private String selectBoxItem(String nodeId) {
        HudNode current = selectBoxNode(nodeId);
        if (current == null || current.selectBox == null) return "";
        int index = selectBoxItemsEditorState.resolve(session.screenId(), nodeId, current.selectBox.items);
        return index < 0 ? "" : current.selectBox.items.get(index);
    }

    private HudNode selectBoxNode(String nodeId) {
        return session.document() != null ? HudLayoutAuthoring.node(session.document(), nodeId) : null;
    }

    private void renameSelectBoxItem(String screenId, String nodeId, String text) {
        HudNode current = selectBoxNode(nodeId);
        if (!isCurrentTarget(screenId, nodeId) || current == null || current.selectBox == null) return;
        int index = selectBoxItemsEditorState.resolve(screenId, nodeId, current.selectBox.items);
        if (index < 0) return;
        String old = current.selectBox.items.get(index);
        if (Objects.equals(old, text)) return;
        if (current.selectBox.items.contains(text)) {
            selectBoxItemsEditorState.setDiagnostic(screenId, nodeId,
                    "Items must be unique; native SelectBox selects strings by value.");
            rebuild();
            return;
        }
        selectBoxItemsEditorState.clearDiagnostic(screenId, nodeId);
        try {
            session.editSelectedNode("Rename HUD SelectBox item",
                    (node, ignored) -> renameSelectBoxItem(node, old, text));
            selectBoxItemsEditorState.clearDiagnostic(screenId, nodeId);
            rebuild();
        } catch (HudEditRejectedException rejected) {
            selectBoxItemsEditorState.setDiagnostic(screenId, nodeId, rejected.getMessage());
            rebuild();
        }
    }

    private void addSelectBoxItem(String screenId, String nodeId) {
        HudNode current = selectBoxNode(nodeId);
        if (!isCurrentTarget(screenId, nodeId) || current == null || current.selectBox == null) return;
        String added = nextItemName(current.selectBox.items);
        session.editSelectedNode("Add HUD SelectBox item", (node, ignored) -> {
            node.selectBox.items.add(added);
            if (node.selectBox.items.size() == 1) node.selectBox.selectedIndex = 0;
        });
    }

    private void removeSelectBoxItem(String screenId, String nodeId) {
        HudNode current = selectBoxNode(nodeId);
        if (!isCurrentTarget(screenId, nodeId) || current == null || current.selectBox == null) return;
        int index = selectBoxItemsEditorState.resolve(screenId, nodeId, current.selectBox.items);
        if (index < 0) return;
        String removed = current.selectBox.items.get(index);
        int nextIndex = current.selectBox.items.size() == 1 ? -1
                : Math.min(index, current.selectBox.items.size() - 2);
        String next = nextIndex < 0 ? null : current.selectBox.items.get(nextIndex);
        selectBoxItemsEditorState.activate(screenId, nodeId, next, nextIndex);
        session.editSelectedNode("Remove HUD SelectBox item",
                (node, ignored) -> removeSelectBoxItem(node, removed));
    }

    private void moveSelectBoxItem(String screenId, String nodeId, int direction) {
        HudNode current = selectBoxNode(nodeId);
        if (!isCurrentTarget(screenId, nodeId) || current == null || current.selectBox == null) return;
        int index = selectBoxItemsEditorState.resolve(screenId, nodeId, current.selectBox.items);
        int target = index + direction;
        if (index < 0 || target < 0 || target >= current.selectBox.items.size()) return;
        String moved = current.selectBox.items.get(index);
        selectBoxItemsEditorState.activate(screenId, nodeId, moved, target);
        session.editSelectedNode("Move HUD SelectBox item",
                (node, ignored) -> moveSelectBoxItem(node, moved, direction));
    }

    private static void renameSelectBoxItem(HudNode node, String old, String text) {
        if (text == null) return;
        int index = node.selectBox.items.indexOf(old);
        if (index < 0) return;
        if (Objects.equals(old, text)) return;
        if (node.selectBox.items.contains(text)) {
            throw new HudEditRejectedException(
                    "SelectBox items must be distinct because native Scene2D selects strings by value.");
        }
        node.selectBox.items.set(index, text);
    }

    private static void removeSelectBoxItem(HudNode node, String item) {
        int index = node.selectBox.items.indexOf(item);
        if (index < 0) return;
        node.selectBox.items.remove(index);
        if (node.selectBox.items.isEmpty()) {
            node.selectBox.selectedIndex = -1;
        } else if (index < node.selectBox.selectedIndex) {
            node.selectBox.selectedIndex--;
        } else if (index == node.selectBox.selectedIndex) {
            node.selectBox.selectedIndex = Math.min(index, node.selectBox.items.size() - 1);
        }
    }

    private static void moveSelectBoxItem(HudNode node, String item, int direction) {
        int from = node.selectBox.items.indexOf(item);
        int to = from + direction;
        if (from < 0 || to < 0 || to >= node.selectBox.items.size()) return;
        String moved = node.selectBox.items.remove(from);
        node.selectBox.items.add(to, moved);
        if (node.selectBox.selectedIndex == from) node.selectBox.selectedIndex = to;
        else if (from < node.selectBox.selectedIndex && to >= node.selectBox.selectedIndex) {
            node.selectBox.selectedIndex--;
        } else if (from > node.selectBox.selectedIndex && to <= node.selectBox.selectedIndex) {
            node.selectBox.selectedIndex++;
        }
    }

    private static String nextItemName(List<String> items) {
        for (int suffix = 1; ; suffix++) {
            String proposed = "Option " + suffix;
            if (!items.contains(proposed)) return proposed;
        }
    }

    private List<HudEditorSession.ImageResourceOption> imageButtonImageOptions(
            games.pixscape.runtime.hud.document.HudImageButtonData data) {
        List<HudEditorSession.ImageResourceOption> options = new java.util.ArrayList<>();
        options.add(new HudEditorSession.ImageResourceOption(null, "Style value"));
        options.addAll(session.imageResourceOptions());
        for (HudEditorSession.ImageButtonImageSlot slot
                : HudEditorSession.ImageButtonImageSlot.values()) {
            var image = slot.get(data);
            if (image != null && imageOption(options, image.resourceName) == null) {
                options.add(new HudEditorSession.ImageResourceOption(
                        image.resourceName, image.resourceName));
            }
        }
        return options;
    }

    private void addImageButtonImageChoice(String nodeId,
                                           HudEditorSession.ImageButtonImageSlot slot, String name,
                                           List<HudEditorSession.ImageResourceOption> options) {
        Supplier<HudEditorSession.ImageResourceOption> reader = nodeValue(nodeId, node -> {
            var image = slot.get(node.imageButton);
            return image == null ? options.get(0) : imageOption(options, image.resourceName);
        });
        addDirectChoice(slot.label(), name, nodeId, reader,
                options.toArray(new HudEditorSession.ImageResourceOption[0]),
                HudEditorSession.ImageResourceOption::label,
                selected -> session.editImageButtonImage(nodeId, slot, selected.resourceName()));
    }

    private List<HudEditorSession.ImageResourceOption> imageTextButtonImageOptions(
            games.pixscape.runtime.hud.document.HudImageTextButtonData data) {
        List<HudEditorSession.ImageResourceOption> options = new java.util.ArrayList<>();
        options.add(new HudEditorSession.ImageResourceOption(null, "Style value"));
        options.addAll(session.imageResourceOptions());
        for (HudEditorSession.ImageButtonImageSlot slot
                : HudEditorSession.ImageButtonImageSlot.values()) {
            var image = slot.get(data);
            if (image != null && imageOption(options, image.resourceName) == null) {
                options.add(new HudEditorSession.ImageResourceOption(
                        image.resourceName, image.resourceName));
            }
        }
        return options;
    }

    private void addImageTextButtonImageChoice(String nodeId,
                                               HudEditorSession.ImageButtonImageSlot slot, String name,
                                               List<HudEditorSession.ImageResourceOption> options) {
        Supplier<HudEditorSession.ImageResourceOption> reader = nodeValue(nodeId, node -> {
            var image = slot.get(node.imageTextButton);
            return image == null ? options.get(0) : imageOption(options, image.resourceName);
        });
        VisSelectBox<Choice<HudEditorSession.ImageResourceOption>> box = new VisSelectBox<>();
        box.setName(name);
        @SuppressWarnings("unchecked") Choice<HudEditorSession.ImageResourceOption>[] choices =
                new Choice[options.size()];
        for (int index = 0; index < options.size(); index++) {
            HudEditorSession.ImageResourceOption option = options.get(index);
            choices[index] = new Choice<>(option, option.label());
        }
        box.setItems(choices);
        HudEditorSession.ImageResourceOption current = reader.get();
        for (Choice<HudEditorSession.ImageResourceOption> choice : choices) {
            if (choice.value.equals(current)) {
                box.setSelected(choice);
                break;
            }
        }
        String screenId = session.screenId();
        box.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (refreshing || !isCurrentTarget(screenId, nodeId)) return;
                Choice<HudEditorSession.ImageResourceOption> selected = box.getSelected();
                if (selected == null) return;
                clearImageDiagnostic(screenId, nodeId, name);
                try {
                    session.editImageTextButtonImage(nodeId, slot, selected.value.resourceName());
                } catch (HudEditRejectedException rejected) {
                    setImageDiagnostic(screenId, nodeId, name, rejected.getMessage());
                    rebuild();
                }
            }
        });
        addFormLabel(slot.label());
        add(box).growX().left().row();
        if (hasImageDiagnostic(screenId, nodeId, name)) {
            VisLabel diagnostic = new VisLabel(imageDiagnostic);
            diagnostic.setName(name + "Error");
            diagnostic.setWrap(true);
            diagnostic.setColor(1f, .55f, .55f, 1f);
            add(diagnostic).growX().colspan(2).padTop(2f).row();
        }
    }

    private void setImageDiagnostic(String screenId, String nodeId, String controlName,
                                    String diagnostic) {
        imageDiagnosticScreenId = screenId;
        imageDiagnosticNodeId = nodeId;
        imageDiagnosticControlName = controlName;
        imageDiagnostic = diagnostic;
    }

    private void clearImageDiagnostic(String screenId, String nodeId, String controlName) {
        if (Objects.equals(imageDiagnosticScreenId, screenId)
                && Objects.equals(imageDiagnosticNodeId, nodeId)
                && Objects.equals(imageDiagnosticControlName, controlName)) {
            imageDiagnosticScreenId = null;
            imageDiagnosticNodeId = null;
            imageDiagnosticControlName = null;
            imageDiagnostic = null;
        }
    }

    private boolean hasImageDiagnostic(String screenId, String nodeId, String controlName) {
        return Objects.equals(imageDiagnosticScreenId, screenId)
                && Objects.equals(imageDiagnosticNodeId, nodeId)
                && Objects.equals(imageDiagnosticControlName, controlName)
                && imageDiagnostic != null && !imageDiagnostic.isBlank();
    }

    private void clearStaleImageDiagnostic(HudNode node) {
        if (imageDiagnostic == null) return;
        if (!Objects.equals(imageDiagnosticScreenId, session.screenId())
                || node == null || !Objects.equals(imageDiagnosticNodeId, node.id)) {
            imageDiagnosticScreenId = null;
            imageDiagnosticNodeId = null;
            imageDiagnosticControlName = null;
            imageDiagnostic = null;
        }
    }

    private static HudEditorSession.ImageResourceOption imageOption(
            List<HudEditorSession.ImageResourceOption> options, String resourceName) {
        for (HudEditorSession.ImageResourceOption option : options) {
            if (java.util.Objects.equals(option.resourceName(), resourceName)) return option;
        }
        return null;
    }

    private void addNumber(String label, String name, String nodeId, Supplier<Float> reader,
                           java.util.function.Consumer<Float> writer, int digits, Predicate<Float> validator) {
        addFormLabel(label);
        add(numberField(name, nodeId, reader, writer, digits, validator)).growX().left().row();
    }

    private SimpleFloatField cellNumberField(String name, String cellId,
                                              Function<HudTableCell, Float> reader,
                                              BiConsumer<HudTableCell, Float> writer,
                                              int digits, Predicate<Float> validator) {
        SimpleFloatField field = new SimpleFloatField().withFractionDigits(digits);
        field.validateCommitWith(value -> value != null && validator.test(value));
        field.setName(name);
        field.bind(cellValue(cellId, reader), value -> {
            if (isCurrentCell(cellId)) session.editSelectedCell("Edit HUD cell property",
                    current -> writer.accept(current, value));
        });
        installNumericLifecycle(field);
        return field;
    }

    private void addCellChecks(String label, String xName, String yName, String cellId,
                               Function<HudTableCell, Boolean> xReader,
                               Function<HudTableCell, Boolean> yReader,
                               CellPairBooleanWriter writer) {
        VisCheckBox x = new VisCheckBox("X:"); x.setName(xName);
        x.setChecked(Boolean.TRUE.equals(cellValue(cellId, xReader).get()));
        VisCheckBox y = new VisCheckBox("Y:"); y.setName(yName);
        y.setChecked(Boolean.TRUE.equals(cellValue(cellId, yReader).get()));
        ChangeListener listener = new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (refreshing || !isCurrentCell(cellId)) return;
                session.editSelectedCell("Edit HUD cell property",
                        current -> writer.write(current, x.isChecked(), y.isChecked()));
            }
        };
        x.addListener(listener); y.addListener(listener);
        VisTable row = new VisTable(); row.defaults().left().padRight(6f); row.add(x); row.add(y);
        addFormLabel(label);
        add(row).padLeft(CommonLayout.PAD_LEFT_SUBMENU).left().row();
    }

    private <E> void addCellChoice(String label, String name, String cellId,
                                   Function<HudTableCell, E> reader, E[] values,
                                   Function<E, String> labels, CellChoiceWriter<E> writer) {
        VisSelectBox<Choice<E>> box = new VisSelectBox<>();
        box.setName(name);
        @SuppressWarnings("unchecked") Choice<E>[] choices = new Choice[values.length];
        for (int index = 0; index < values.length; index++) {
            choices[index] = new Choice<>(values[index], labels.apply(values[index]));
        }
        box.setItems(choices);
        E current = cellValue(cellId, reader).get();
        for (Choice<E> choice : choices) if (choice.value.equals(current)) { box.setSelected(choice); break; }
        box.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (refreshing || !isCurrentCell(cellId)) return;
                Choice<E> selected = box.getSelected();
                if (selected != null) session.editSelectedCell("Edit HUD cell property",
                        cell -> writer.write(cell, selected.value));
            }
        });
        addFormLabel(label);
        add(box).growX().left().row();
    }

    private void addExactNumber(String label, String name, String nodeId, Supplier<Float> reader,
                                java.util.function.Consumer<Float> writer,
                                Predicate<Float> validator) {
        SimpleFloatField field = new SimpleFloatField().useExactText();
        addFormLabel(label);
        add(bindNumberField(field, name, nodeId, reader, writer, validator)).growX().left().row();
    }

    private SimpleFloatField numberField(String name, String nodeId, Supplier<Float> reader,
                                         java.util.function.Consumer<Float> writer, int digits,
                                         Predicate<Float> validator) {
        SimpleFloatField field = new SimpleFloatField().withFractionDigits(digits);
        return bindNumberField(field, name, nodeId, reader, writer, validator);
    }

    private SimpleFloatField bindNumberField(SimpleFloatField field, String name, String nodeId,
                                              Supplier<Float> reader,
                                              java.util.function.Consumer<Float> writer,
                                              Predicate<Float> validator) {
        field.validateCommitWith(value -> value != null && validator.test(value));
        field.setName(name);
        field.bind(reader, value -> { if (isCurrentTarget(nodeId)) writer.accept(value); });
        installNumericLifecycle(field);
        return field;
    }

    private void addText(String label, String name, String nodeId, Supplier<String> reader,
                         java.util.function.Consumer<String> writer) {
        SimpleTextField field = new SimpleTextField();
        String screenId = session.screenId();
        field.setName(name);
        field.bind(reader, value -> { if (isCurrentTarget(screenId, nodeId)) writer.accept(value); });
        installTextLifecycle(field);
        addFormLabel(label);
        add(field).growX().left().row();
    }

    private void addNonNegativeInteger(String label, String name, String nodeId,
                                       Supplier<Integer> reader,
                                       java.util.function.Consumer<Integer> writer) {
        SimpleTextField field = new SimpleTextField();
        String screenId = session.screenId();
        field.setName(name);
        field.setTextFieldFilter(new VisTextField.TextFieldFilter.DigitsOnlyFilter());
        field.bind(() -> {
            Integer value = reader.get();
            return value != null ? Integer.toString(value) : null;
        }, text -> {
            if (!isCurrentTarget(screenId, nodeId)) return;
            try {
                writer.accept(text == null || text.isEmpty() ? 0 : Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                // SimpleTextField refreshes from the authored value after a rejected commit.
            }
        });
        installTextLifecycle(field);
        addFormLabel(label);
        add(field).growX().left().row();
    }

    private void addMultilineText(String label, String name, String nodeId,
                                  Supplier<String> reader,
                                  java.util.function.Consumer<String> writer) {
        SimpleTextArea field = new SimpleTextArea().withMultilineEditing();
        String screenId = session.screenId();
        field.setName(name);
        field.setPrefRows(3);
        field.bind(reader, value -> {
            if (isCurrentTarget(screenId, nodeId)) writer.accept(value);
        });
        installTextAreaLifecycle(field);
        addFormLabel(label).top();
        add(field).growX().left().row();
    }

    private <E> void addChoice(String label, String name, String nodeId, Supplier<E> reader,
                               E[] values, Function<E, String> labels, ChoiceWriter<E> writer) {
        VisSelectBox<Choice<E>> box = new VisSelectBox<>();
        box.setName(name);
        @SuppressWarnings("unchecked") Choice<E>[] choices = new Choice[values.length];
        for (int index = 0; index < values.length; index++) choices[index] = new Choice<>(values[index], labels.apply(values[index]));
        box.setItems(choices);
        E current = reader.get();
        for (Choice<E> choice : choices) if (choice.value.equals(current)) { box.setSelected(choice); break; }
        box.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (refreshing || !isCurrentTarget(nodeId)) return;
                Choice<E> selected = box.getSelected();
                if (selected != null) edit(nodeId, (node, relation) -> writer.write(node, relation, selected.value));
            }
        });
        addFormLabel(label);
        add(box).growX().left().row();
    }

    private <E> void addDirectChoice(String label, String name, String nodeId, Supplier<E> reader,
                                     E[] values, Function<E, String> labels,
                                     java.util.function.Consumer<E> writer) {
        VisSelectBox<Choice<E>> box = new VisSelectBox<>();
        box.setName(name);
        @SuppressWarnings("unchecked") Choice<E>[] choices = new Choice[values.length];
        for (int index = 0; index < values.length; index++) {
            choices[index] = new Choice<>(values[index], labels.apply(values[index]));
        }
        box.setItems(choices);
        E current = reader.get();
        for (Choice<E> choice : choices) {
            if (choice.value.equals(current)) { box.setSelected(choice); break; }
        }
        box.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (refreshing || !isCurrentTarget(nodeId)) return;
                Choice<E> selected = box.getSelected();
                if (selected != null) writer.accept(selected.value);
            }
        });
        addFormLabel(label);
        add(box).growX().left().row();
    }

    private void addCheck(String label, String name, String nodeId, Supplier<Boolean> reader, BooleanWriter writer) {
        VisCheckBox check = new VisCheckBox(label);
        check.setName(name);
        check.setChecked(Boolean.TRUE.equals(reader.get()));
        check.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (refreshing || !isCurrentTarget(nodeId)) return;
                edit(nodeId, (node, relation) -> writer.write(node, relation, check.isChecked()));
            }
        });
        add(check).colspan(2).left().row();
    }

    private void addDirectCheck(String label, String name, String nodeId,
                                Supplier<Boolean> reader,
                                java.util.function.Consumer<Boolean> writer) {
        VisCheckBox check = new VisCheckBox(label);
        check.setName(name);
        check.setChecked(Boolean.TRUE.equals(reader.get()));
        check.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (refreshing || !isCurrentTarget(nodeId)) return;
                writer.accept(check.isChecked());
            }
        });
        add(check).colspan(2).left().row();
    }

    private void addChecks(String label, String xName, String yName, String nodeId,
                           Supplier<Boolean> xReader, Supplier<Boolean> yReader, PairBooleanWriter writer) {
        VisCheckBox x = new VisCheckBox("X:"); x.setName(xName); x.setChecked(Boolean.TRUE.equals(xReader.get()));
        VisCheckBox y = new VisCheckBox("Y:"); y.setName(yName); y.setChecked(Boolean.TRUE.equals(yReader.get()));
        ChangeListener listener = new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (refreshing || !isCurrentTarget(nodeId)) return;
                edit(nodeId, (node, relation) -> writer.write(node, relation, x.isChecked(), y.isChecked()));
            }
        };
        x.addListener(listener); y.addListener(listener);
        VisTable row = new VisTable(); row.defaults().left().padRight(6f); row.add(x); row.add(y);
        addFormLabel(label);
        add(row).padLeft(CommonLayout.PAD_LEFT_SUBMENU).left().row();
    }

    private void addPair(String label, String firstLabel, Actor first, String secondLabel, Actor second) {
        HorizontalGroup group = new HorizontalGroup()
                .space(6f)
                .wrapSpace(PROPERTY_CELL_PAD * 2f)
                .wrap(true);
        group.addActor(labeled(firstLabel, first));
        group.addActor(labeled(secondLabel, second));
        addFormLabel(label).top();
        add(group).padLeft(CommonLayout.PAD_LEFT_SUBMENU).growX().left().row();
    }

    private void addPadding(Actor left, Actor right, Actor top, Actor bottom) {
        VisTable padding = new VisTable();
        padding.defaults().left();
        padding.add(labeled("Left", left)).padRight(6f).padBottom(PROPERTY_CELL_PAD * 2f);
        padding.add(labeled("Right", right)).padBottom(PROPERTY_CELL_PAD * 2f).row();
        padding.add(labeled("Top", top)); padding.add(labeled("Bottom", bottom));
        addFormLabel("Padding").top();
        add(padding).padLeft(CommonLayout.PAD_LEFT_SUBMENU).growX().left().row();
    }

    private VisTable labeled(String label, Actor control) {
        VisTable pair = new VisTable();
        pair.add(new VisLabel(formLabel(label))).left().padRight(PROPERTY_CELL_PAD);
        pair.add(control).minWidth(48f).left();
        return pair;
    }

    private void addReadOnly(String label, String value) {
        addFormLabel(label);
        VisLabel display = new VisLabel(text(value)); display.setWrap(true);
        add(display).growX().left().row();
    }

    private Cell<VisLabel> addFormLabel(String label) {
        return add(new VisLabel(formLabel(label))).minWidth(CommonLayout.LABEL_WIDTH).left();
    }

    private void addPropertySeparator() { add(new Separator()).growX().colspan(2).padTop(5f).padBottom(5f).row(); }

    private void addError() {
        if (session.status() != HudEditorSession.Status.ERROR) return;
        addPropertySeparator();
        VisLabel error = new VisLabel(session.errorMessage()); error.setWrap(true); error.setColor(1f, .55f, .55f, 1f);
        add(error).growX().colspan(2).padTop(6f).row();
    }

    private void installNumericLifecycle(SimpleFloatField field) {
        field.addListener(new InputListener() {
            @Override public boolean keyDown(InputEvent event, int keycode) {
                if (keycode != Input.Keys.ESCAPE) return false;
                field.refresh();
                if (field.getStage() != null) field.getStage().setKeyboardFocus(null);
                event.stop();
                return true;
            }
        });
        field.addListener(new FocusListener() {
            @Override public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                if (!focused && !refreshing) field.commit();
            }
        });
    }

    private void installTextLifecycle(SimpleTextField field) {
        field.addListener(new InputListener() {
            @Override public boolean keyDown(InputEvent event, int keycode) {
                if (keycode != Input.Keys.ESCAPE) return false;
                field.rollback();
                if (field.getStage() != null) field.getStage().setKeyboardFocus(null);
                event.stop();
                return true;
            }
        });
        field.addListener(new FocusListener() {
            @Override public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                if (!focused && !refreshing) field.commit();
            }
        });
    }

    private void installTextAreaLifecycle(SimpleTextArea field) {
        field.addListener(new InputListener() {
            @Override public boolean keyDown(InputEvent event, int keycode) {
                if (keycode != Input.Keys.ESCAPE) return false;
                field.rollback();
                if (field.getStage() != null) field.getStage().setKeyboardFocus(null);
                event.stop();
                return true;
            }
        });
        field.addListener(new FocusListener() {
            @Override public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                if (!focused && !refreshing) field.commit();
            }
        });
    }

    private boolean isCurrentTarget(String nodeId) { return nodeId != null && nodeId.equals(session.selectedNodeId()); }
    private boolean isCurrentCell(String cellId) { return cellId != null && cellId.equals(session.selectedCellId()); }
    private boolean isCurrentTarget(String screenId, String nodeId) {
        return Objects.equals(screenId, session.screenId()) && isCurrentTarget(nodeId);
    }
    private void edit(String nodeId, BiConsumer<HudNode, HudChild> mutation) {
        if (isCurrentTarget(nodeId)) session.editSelectedNode("Edit HUD node property", mutation);
    }
    private <T> Supplier<T> nodeValue(String nodeId, Function<HudNode, T> reader) {
        return () -> { HudNode node = HudLayoutAuthoring.node(session.document(), nodeId); return node != null ? reader.apply(node) : null; };
    }
    private <T> Supplier<T> relationValue(String nodeId, Function<HudChild, T> reader) {
        return () -> { HudChild relation = HudLayoutAuthoring.childRelation(session.document(), nodeId); return relation != null ? reader.apply(relation) : null; };
    }
    private <T> Supplier<T> cellValue(String cellId, Function<HudTableCell, T> reader) {
        return () -> {
            HudTableCell cell = HudLayoutAuthoring.cell(session.document(), cellId);
            return cell != null ? reader.apply(cell) : null;
        };
    }
    private static Predicate<Float> nonNegative() { return value -> value != null && value >= 0f; }
    private static Predicate<Float> finite() { return value -> value != null && Float.isFinite(value); }
    private static Predicate<Float> within(float minimum, float maximum) { return value -> value != null && value >= minimum && value <= maximum; }
    private static String formLabel(String label) { return label.endsWith(":") ? label : label + ":"; }
    private static String text(String value) { return value == null || value.isBlank() ? "—" : value; }
    private static LabelStyleOption labelStyleOption(String styleName) {
        return styleName == null || styleName.isBlank()
                ? new LabelStyleOption(null, "Default")
                : new LabelStyleOption(styleName, styleName);
    }
    private static String enumLabel(Enum<?> value) {
        String name = value.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
    private static ChangeListener change(Runnable action) {
        return new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { action.run(); }
        };
    }

    /** Ephemeral inspector state; authored selection remains in {@link HudNode#selectBox}. */
    private static final class SelectBoxItemsEditorState {
        private String screenId;
        private String nodeId;
        private String activeItem;
        private String diagnostic;
        private int activeIndex = -1;

        int resolve(String screenId, String nodeId, List<String> items) {
            if (!matches(screenId, nodeId)) {
                this.screenId = screenId;
                this.nodeId = nodeId;
                activeItem = null;
                diagnostic = null;
                activeIndex = 0;
            }
            if (items.isEmpty()) {
                activeItem = null;
                activeIndex = -1;
                return -1;
            }
            int located = activeItem == null ? -1 : items.indexOf(activeItem);
            if (located >= 0) activeIndex = located;
            else activeIndex = Math.max(0, Math.min(activeIndex, items.size() - 1));
            activeItem = items.get(activeIndex);
            return activeIndex;
        }

        void activate(String screenId, String nodeId, String item, int index) {
            this.screenId = screenId;
            this.nodeId = nodeId;
            activeItem = item;
            activeIndex = index;
            diagnostic = null;
        }

        void setDiagnostic(String screenId, String nodeId, String diagnostic) {
            if (!matches(screenId, nodeId)) return;
            this.diagnostic = diagnostic;
        }

        void clearDiagnostic(String screenId, String nodeId) {
            if (matches(screenId, nodeId)) diagnostic = null;
        }

        boolean hasDiagnostic(String screenId, String nodeId) {
            return matches(screenId, nodeId) && diagnostic != null && !diagnostic.isBlank();
        }

        String diagnostic() { return diagnostic; }

        private boolean matches(String screenId, String nodeId) {
            return Objects.equals(this.screenId, screenId) && Objects.equals(this.nodeId, nodeId);
        }
    }

    private record Choice<E>(E value, String label) { @Override public String toString() { return label; } }
    private record SkinChoice(Integer assetId, String skinId, String label, boolean missing) { }
    private record LabelStyleOption(String styleName, String label) { }
    private enum CellDimension { MIN_WIDTH, MIN_HEIGHT, PREF_WIDTH, PREF_HEIGHT }
    @FunctionalInterface private interface ChoiceWriter<E> { void write(HudNode node, HudChild relation, E selected); }
    @FunctionalInterface private interface BooleanWriter { void write(HudNode node, HudChild relation, boolean checked); }
    @FunctionalInterface private interface PairBooleanWriter { void write(HudNode node, HudChild relation, boolean x, boolean y); }
    @FunctionalInterface private interface CellPairBooleanWriter { void write(HudTableCell cell, boolean x, boolean y); }
    @FunctionalInterface private interface CellChoiceWriter<E> { void write(HudTableCell cell, E selected); }
}
