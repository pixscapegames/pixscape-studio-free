package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisList;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.runtime.hud.document.HudCellConstraints;
import games.pixscape.runtime.hud.document.HudTableRow;
import games.pixscape.runtime.hud.document.HudTableCell;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudLabelData;
import games.pixscape.runtime.hud.document.HudImageButtonData;
import games.pixscape.runtime.hud.document.HudImageTextButtonData;
import games.pixscape.runtime.hud.document.HudImageData;
import games.pixscape.runtime.hud.document.HudImageSource;
import games.pixscape.runtime.hud.document.HudSelectBoxData;
import games.pixscape.runtime.hud.document.HudListData;
import games.pixscape.runtime.hud.document.HudCheckBoxData;
import games.pixscape.runtime.hud.document.HudSliderData;
import games.pixscape.runtime.hud.document.HudProgressBarData;
import games.pixscape.runtime.hud.document.HudScrollPaneData;
import games.pixscape.runtime.hud.document.HudWindowData;
import games.pixscape.runtime.hud.document.HudDialogData;
import games.pixscape.runtime.hud.document.HudWindowActionKind;
import games.pixscape.runtime.hud.document.HudSliderOrientation;
import games.pixscape.runtime.hud.document.HudTextButtonData;
import games.pixscape.runtime.hud.document.HudTextFieldData;
import games.pixscape.runtime.hud.document.HudTextraLabelData;
import games.pixscape.runtime.hud.document.HudResourceCatalog;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.service.hud.HudEditorSession;
import games.pixscape.studio.service.hud.HudLayoutAuthoring;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.SimpleFloatField;
import games.pixscape.studio.ui.widget.SimpleTextArea;
import games.pixscape.studio.ui.widget.SimpleTextField;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HudInspectorViewTest {

    @Test
    public void dialogResultButtonsEditOrderAndDeleteWithUndoRedo() throws Exception {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode dialog = new HudNode("dialog", HudNodeKind.DIALOG);
        dialog.dialog = new HudDialogData();
        dialog.table = HudLayoutAuthoring.newTableLayout(dialog, 1, 1, false);
        root.children.add(HudChild.free(dialog, new HudFreePlacement()));
        HudScreenEditorDocument document = new HudScreenEditorDocument("hud/results", "HUD",
                asset("hud/results"), new HudDocumentV1(root));
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudPanelTestSupport.projectHistoryNavigation(session, document);
        session.selectNode("dialog");
        HudInspectorView view = new HudInspectorView(session);
        ((VisTextButton) view.findActor("hudAddDialogResultButton")).setChecked(true);
        String first = document.document().root.children.get(0).node.dialog.resultButtons.get(0).button.id;
        assertEquals(first, session.selectedNodeId());
        SimpleTextField resultId = view.findActor("hudDialogResultId");
        resultId.setText("confirm");
        resultId.commit();
        ((VisCheckBox) view.findActor("hudDialogResultClose")).setChecked(false);
        assertEquals("confirm", document.document().root.children.get(0).node.dialog.resultButtons.get(0).resultId);
        assertFalse(document.document().root.children.get(0).node.dialog.resultButtons.get(0).closeAfterActivation);

        session.selectNode("dialog");
        @SuppressWarnings("unchecked") VisSelectBox<HudNodeKind> kind =
                view.findActor("hudDialogResultKind");
        kind.setSelected(HudNodeKind.IMAGE_TEXT_BUTTON);
        ((VisTextButton) view.findActor("hudAddDialogResultButton")).setChecked(true);
        String second = document.document().root.children.get(0).node.dialog.resultButtons.get(1).button.id;
        assertEquals(HudNodeKind.IMAGE_TEXT_BUTTON,
                document.document().root.children.get(0).node.dialog.resultButtons.get(1).button.kind);
        assertTrue(session.hierarchy().stream().anyMatch(entry -> entry.nodeId().equals(second)));
        SimpleTextField duplicate = view.findActor("hudDialogResultId");
        duplicate.setText("confirm");
        duplicate.commit();
        assertEquals(4, document.editSession().historySize());
        assertEquals("result-1", document.document().root.children.get(0).node.dialog.resultButtons.get(1).resultId);
        assertNotNull(view.findActor("hudDialogResultError"));
        session.selectNode("dialog");
        ((VisTextButton) view.findActor("hudMoveDialogResultUp1")).setChecked(true);
        assertEquals(second, document.document().root.children.get(0).node.dialog.resultButtons.get(0).button.id);
        assertEquals(first, document.document().root.children.get(0).node.dialog.resultButtons.get(1).button.id);
        assertEquals(5, document.editSession().historySize());
        assertEquals("dialog", session.selectedNodeId());
        assertTrue(session.canDeleteNode(first));
        ((VisTextButton) view.findActor("hudDeleteDialogResult1")).setChecked(true);
        assertEquals(6, document.editSession().historySize());
        assertNull(HudLayoutAuthoring.node(document.document(), first));
        assertTrue(document.editSession().undo());
        assertNotNull(HudLayoutAuthoring.node(document.document(), first));
        assertTrue(document.editSession().redo());
        assertNull(HudLayoutAuthoring.node(document.document(), first));
        HudDocumentV1 reopened = new HudDocumentCodec().read(
                new HudDocumentCodec().write(document.document()));
        assertEquals(second, reopened.root.children.get(0).node.dialog.resultButtons.get(0).button.id);
    }
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void titlesUseTheConcreteUppercaseSchemaKind() {
        assertEquals("HUD SCREEN", HudInspectorView.titleFor(null));
        for (HudNodeKind kind : HudNodeKind.values()) {
            assertEquals(kind == HudNodeKind.TEXT_BUTTON ? "TEXTBUTTON"
                            : kind == HudNodeKind.IMAGE_BUTTON ? "IMAGEBUTTON"
                            : kind == HudNodeKind.TEXT_FIELD ? "TEXTFIELD"
                            : kind == HudNodeKind.SELECT_BOX ? "SELECTBOX"
                            : kind == HudNodeKind.CHECK_BOX ? "CHECKBOX"
                            : kind.name().replace('_', ' '),
                    HudInspectorView.titleFor(new HudNode("node", kind)));
        }
    }

    @Test
    public void screenAndNodeInspectorUseTheStandardTitleAndFormRows() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument document = manager.openHudScreen("hud/main", "HUD");
        HudNode table = table("root");
        table.actor.width = 128f;
        table.actor.height = 64f;
        document.editSession().edit("Create root", ignored -> new HudDocumentV1(table));
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudInspectorView view = new HudInspectorView(session);

        assertTitle(view, "HUD SCREEN");

        session.selectNode("root");
        assertTitle(view, "TABLE");
        List<String> labels = labels(view);
        assertTrue(labels.contains("Node ID:"));
        assertTrue(labels.contains("Width:"));
        assertTrue(labels.contains("Height:"));
        assertFalse(labels.contains("Kind:"));
    }

    @Test
    public void screenSkinSelectorIsUndoableAndRestoresAuthoredValueAfterRejection()
            throws Exception {
        AssetMetaDatabase database = new AssetMetaDatabase();
        AssetMeta first = database.registerIfAbsent(AssetType.SKIN, "skins/one/Game",
                "orig/skins/1/game.json", AssetMeta.AssetScope.USER);
        AssetMeta second = database.registerIfAbsent(AssetType.SKIN, "skins/two/Game",
                "orig/skins/2/game.json", AssetMeta.AssetScope.USER);
        database.registerIfAbsent(AssetType.SKIN, "skins/Bad",
                "orig/skins/3/bad.json", AssetMeta.AssetScope.USER);
        var screenAsset = asset("hud/main");
        screenAsset.skinId = first.sourceRelPath();
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "HUD", screenAsset, new HudDocumentV1(
                new HudNode("root", HudNodeKind.GROUP)));
        HudEditorSession session = HudPanelTestSupport.projectedSession(document,
                new HudEditorSession(() -> database));
        HudPanelTestSupport.bindSkinValidatingPreview(session, document,
                candidate -> second.sourceRelPath().equals(candidate.skinId)
                        || first.sourceRelPath().equals(candidate.skinId));
        HudInspectorView view = new HudInspectorView(session);

        @SuppressWarnings("rawtypes") VisSelectBox skin = view.findActor("hudScreenSkin");
        String firstLabel = "Game — skins/one/Game (#" + first.id() + ")";
        String secondLabel = "Game — skins/two/Game (#" + second.id() + ")";
        assertEquals(firstLabel, skin.getSelected().toString());
        assertNull(view.findActor("hudScreenSkinImport"));
        skin.setSelectedIndex(choiceIndex(skin, secondLabel));
        assertEquals(second.sourceRelPath(), document.asset().skinId);
        assertEquals(1, document.editSession().historySize());
        assertTrue(document.editSession().undo());
        assertEquals(first.sourceRelPath(), document.asset().skinId);
        assertTrue(document.editSession().redo());
        assertEquals(second.sourceRelPath(), document.asset().skinId);

        skin = view.findActor("hudScreenSkin");
        skin.setSelectedIndex(choiceIndex(skin, "Bad"));
        assertEquals(second.sourceRelPath(), document.asset().skinId);
        assertEquals(1, document.editSession().historySize());
        assertEquals(secondLabel,
                ((VisSelectBox<?>) view.findActor("hudScreenSkin")).getSelected().toString());
        assertNotNull(view.findActor("hudScreenSkinError"));

        skin = view.findActor("hudScreenSkin");
        skin.setSelectedIndex(choiceIndex(skin, "None"));
        assertEquals(second.sourceRelPath(), document.asset().skinId);
        assertEquals(1, document.editSession().historySize());
        assertNotNull(view.findActor("hudScreenSkinError"));
    }

    @Test
    public void screenSkinSelectorKeepsMissingReferenceVisibleAndCannotCrossDocuments()
            throws Exception {
        AssetMetaDatabase database = new AssetMetaDatabase();
        database.registerIfAbsent(AssetType.SKIN, "skins/Available",
                "orig/skins/4/available.json", AssetMeta.AssetScope.USER);
        var missingAsset = asset("hud/first");
        missingAsset.skinId = "orig/skins/99/missing.json";
        HudScreenEditorDocument first = new HudScreenEditorDocument(
                "hud/first", "First", missingAsset,
                new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP)));
        HudEditorSession session = HudPanelTestSupport.projectedSession(first,
                new HudEditorSession(() -> database));
        HudInspectorView view = new HudInspectorView(session);
        @SuppressWarnings("rawtypes") VisSelectBox stale = view.findActor("hudScreenSkin");
        assertEquals("Missing: orig/skins/99/missing.json", stale.getSelected().toString());

        HudScreenEditorDocument second = new HudScreenEditorDocument(
                "hud/second", "Second", asset("hud/second"),
                new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP)));
        HudPanelTestSupport.projectDocument(session, second);
        view.rebuild();
        stale.setSelectedIndex(choiceIndex(stale, "None"));

        assertEquals("orig/skins/99/missing.json", first.asset().skinId);
        assertNull(second.asset().skinId);
        assertEquals(0, first.editSession().historySize());
        assertEquals(0, second.editSession().historySize());
    }

    @Test
    public void freeNumericEnumAndStaleSelectionEditsUseTheHudHistory() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode first = new HudNode("first", HudNodeKind.GROUP);
        HudNode second = new HudNode("second", HudNodeKind.GROUP);
        root.children.add(HudChild.free(first, new HudFreePlacement()));
        root.children.add(HudChild.free(second, new HudFreePlacement()));
        HudScreenEditorDocument document = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"),
                new HudDocumentV1(root)));
        document.setSelectedNodeId("first");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudInspectorView view = new HudInspectorView(session);

        SimpleFloatField width = view.findActor("hudWidthField");
        width.setText("123.5");
        width.commit();
        assertEquals(123.5f, node(document.document().root, "first").actor.width, 0f);
        assertEquals(1, document.editSession().historySize());
        assertTrue(document.editSession().undo());
        assertEquals(0f, node(document.document().root, "first").actor.width, 0f);
        assertTrue(document.editSession().redo());
        assertEquals(123.5f, node(document.document().root, "first").actor.width, 0f);

        @SuppressWarnings("rawtypes") VisSelectBox anchor = view.findActor("hudHorizontalAnchor");
        anchor.setSelectedIndex(2);
        assertEquals("RIGHT", relation(document.document().root, "first").free.horizontalAnchor.name());

        width.setText("777");
        session.selectNode("second");
        width.commit();
        assertEquals(123.5f, node(document.document().root, "first").actor.width, 0f);
        assertEquals(0f, node(document.document().root, "second").actor.width, 0f);
    }

    @Test
    public void labelFontAssignmentTargetsCapturedNodeAndUsesUndoRedo() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode first = new HudNode("first", HudNodeKind.LABEL);
        first.label = new games.pixscape.runtime.hud.document.HudLabelData();
        first.label.text = "First";
        HudNode second = new HudNode("second", HudNodeKind.LABEL);
        second.label = new games.pixscape.runtime.hud.document.HudLabelData();
        second.label.text = "Second";
        root.children.add(HudChild.direct(first));
        root.children.add(HudChild.direct(second));
        HudScreenEditorDocument document = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"), new HudDocumentV1(root)));
        document.setSelectedNodeId("second");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);

        assertTrue(session.assignFont("hud/main", "first", 42));
        assertEquals(Integer.valueOf(42), node(document.document().root, "first").label.fontAssetId);
        assertNull(node(document.document().root, "second").label.fontAssetId);
        assertTrue(document.editSession().undo());
        assertNull(node(document.document().root, "first").label.fontAssetId);
        assertTrue(document.editSession().redo());
        assertEquals(Integer.valueOf(42), node(document.document().root, "first").label.fontAssetId);
        assertTrue(session.assignFont("hud/main", "first", null));
        assertNull(node(document.document().root, "first").label.fontAssetId);
    }

    @Test public void labelFontSelectorKeepsStyleFontAndRefreshesFromTheAssetCatalog() throws Exception {
        AssetMetaDatabase database = new AssetMetaDatabase();
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument document = manager.openHudScreen(labelDocument("hud/main", "Label"));
        document.setSelectedNodeId("label-1");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document,
                new HudEditorSession(() -> database));
        HudPanelTestSupport.projectLabelResources(session);
        HudInspectorView view = new HudInspectorView(session);

        @SuppressWarnings("rawtypes") VisSelectBox font = view.findActor("hudLabelFont");
        assertFalse(font.isDisabled());
        assertEquals(1, font.getItems().size);
        assertEquals("Style font", font.getItems().first().toString());
        assertNull(view.findActor("hudLabelFontImport"));

        database.registerIfAbsent(AssetType.FONT, "fonts/Dialogue", "orig/fonts/1/dialogue.fnt",
                AssetMeta.AssetScope.USER);
        session.refreshAssetOptions();

        font = view.findActor("hudLabelFont");
        assertEquals(2, font.getItems().size);
        assertEquals("Style font", font.getItems().first().toString());
        assertEquals("fonts/Dialogue", font.getItems().get(1).toString());
        assertNull(view.findActor("hudLabelFontImport"));
        assertEquals(0, document.editSession().historySize());
    }

    @Test public void labelFontSelectorRestoresAuthoredChoiceAfterRejectedStyleFont() throws Exception {
        AssetMetaDatabase database = new AssetMetaDatabase();
        AssetMeta firstFont = database.registerIfAbsent(AssetType.FONT, "fonts/First",
                "orig/fonts/1/first.fnt", AssetMeta.AssetScope.USER);
        AssetMeta secondFont = database.registerIfAbsent(AssetType.FONT, "fonts/Second",
                "orig/fonts/2/second.fnt", AssetMeta.AssetScope.USER);
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode label = new HudNode("label-1", HudNodeKind.LABEL);
        label.label = new HudLabelData();
        label.label.text = "Label";
        label.label.styleName = "fontless";
        label.label.fontAssetId = firstFont.id();
        root.children.add(HudChild.free(label, new HudFreePlacement()));
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument document = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"), new HudDocumentV1(root)));
        document.setSelectedNodeId("label-1");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document,
                new HudEditorSession(() -> database));
        MutableLabelCatalog resources = new MutableLabelCatalog(firstFont.id(), secondFont.id());
        HudPanelTestSupport.bindResourceValidatingPreview(session, document, resources);
        HudInspectorView view = new HudInspectorView(session);

        @SuppressWarnings("rawtypes") VisSelectBox font = view.findActor("hudLabelFont");
        assertEquals("fonts/First", font.getSelected().toString());
        font.setSelectedIndex(0);

        assertEquals(Integer.valueOf(firstFont.id()),
                node(document.document().root, "label-1").label.fontAssetId);
        assertEquals(0, document.editSession().historySize());
        font = view.findActor("hudLabelFont");
        assertEquals("fonts/First", font.getSelected().toString());
        VisLabel diagnostic = view.findActor("hudLabelFontError");
        assertNotNull(diagnostic);
        assertTrue(diagnostic.getText().toString().contains("fontless"));

        font.setSelectedIndex(2);
        assertEquals(Integer.valueOf(secondFont.id()),
                node(document.document().root, "label-1").label.fontAssetId);
        assertEquals(1, document.editSession().historySize());
        assertNull(view.findActor("hudLabelFontError"));
        assertTrue(document.editSession().undo());
        assertEquals(Integer.valueOf(firstFont.id()),
                node(document.document().root, "label-1").label.fontAssetId);
        assertTrue(document.editSession().redo());
        assertEquals(Integer.valueOf(secondFont.id()),
                node(document.document().root, "label-1").label.fontAssetId);

        resources.styleHasFont = true;
        font = view.findActor("hudLabelFont");
        font.setSelectedIndex(0);
        assertNull(node(document.document().root, "label-1").label.fontAssetId);
        assertEquals(2, document.editSession().historySize());
        assertTrue(document.editSession().undo());
        assertEquals(Integer.valueOf(secondFont.id()),
                node(document.document().root, "label-1").label.fontAssetId);
        assertTrue(document.editSession().redo());
        assertNull(node(document.document().root, "label-1").label.fontAssetId);
    }

    @Test public void textFieldFontSelectorRecoversAfterRejectedStyleFontRemoval() throws Exception {
        AssetMetaDatabase database = new AssetMetaDatabase();
        AssetMeta firstFont = database.registerIfAbsent(AssetType.FONT, "fonts/First",
                "orig/fonts/1/first.fnt", AssetMeta.AssetScope.USER);
        AssetMeta secondFont = database.registerIfAbsent(AssetType.FONT, "fonts/Second",
                "orig/fonts/2/second.fnt", AssetMeta.AssetScope.USER);
        HudNode field = new HudNode("field", HudNodeKind.TEXT_FIELD);
        field.textField = new HudTextFieldData();
        field.textField.styleName = "fontless";
        field.textField.fontAssetId = firstFont.id();
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument document = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"), new HudDocumentV1(field)));
        document.setSelectedNodeId("field");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document,
                new HudEditorSession(() -> database));
        MutableLabelCatalog resources = new MutableLabelCatalog(firstFont.id(), secondFont.id());
        HudPanelTestSupport.bindResourceValidatingPreview(session, document, resources);
        HudInspectorView view = new HudInspectorView(session);

        @SuppressWarnings("rawtypes") VisSelectBox font = view.findActor("hudTextFieldFont");
        assertEquals("fonts/First", font.getSelected().toString());
        font.setSelectedIndex(0);
        assertEquals(Integer.valueOf(firstFont.id()),
                document.document().root.textField.fontAssetId);
        assertEquals(0, document.editSession().historySize());
        font = view.findActor("hudTextFieldFont");
        assertEquals("fonts/First", font.getSelected().toString());
        assertNotNull(view.findActor("hudTextFieldFontError"));

        font.setSelectedIndex(2);
        assertEquals(Integer.valueOf(secondFont.id()),
                document.document().root.textField.fontAssetId);
        assertEquals(1, document.editSession().historySize());
        assertNull(view.findActor("hudTextFieldFontError"));
        assertTrue(document.editSession().undo());
        assertEquals(Integer.valueOf(firstFont.id()),
                document.document().root.textField.fontAssetId);
        assertTrue(document.editSession().redo());
        assertEquals(Integer.valueOf(secondFont.id()),
                document.document().root.textField.fontAssetId);

        resources.styleHasFont = true;
        font = view.findActor("hudTextFieldFont");
        font.setSelectedIndex(0);
        assertNull(document.document().root.textField.fontAssetId);
        assertTrue(document.editSession().undo());
        assertEquals(Integer.valueOf(secondFont.id()),
                document.document().root.textField.fontAssetId);
        assertTrue(document.editSession().redo());
        assertNull(document.document().root.textField.fontAssetId);
    }

    @Test
    public void cellPaddingAndFillControlsEditTheCorrectAuthoredMembers() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = table("root");
        HudNode child = new HudNode("child", HudNodeKind.GROUP);
        root.table.rows.get(0).cells.get(0).content = child;
        HudScreenEditorDocument document = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"),
                new HudDocumentV1(root)));
        document.setSelectedNodeId("child");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudPanelTestSupport.selectCell(session, document, root.table.rows.get(0).cells.get(0).id);
        HudInspectorView view = new HudInspectorView(session);
        assertNull(view.findActor("hudCell-" + root.table.rows.get(0).cells.get(0).id));
        assertNull(view.findActor("hudInsertRowBefore"));
        assertNull(view.findActor("hudDeleteColumn"));
        assertTrue(labels(view).contains("Position:"));

        SimpleFloatField left = view.findActor("hudPadLeftField");
        left.setText("12");
        left.commit();
        HudCellConstraints cell = relation(document.document().root, "child").cell;
        assertEquals(12f, cell.padLeft, 0f);
        assertEquals(0f, cell.padRight, 0f);

        VisCheckBox fillX = view.findActor("hudFillX");
        fillX.setChecked(true);
        cell = relation(document.document().root, "child").cell;
        assertTrue(cell.fillX);
        assertFalse(cell.fillY);

        int historyBeforeInvalid = document.editSession().historySize();
        SimpleFloatField currentLeft = view.findActor("hudPadLeftField");
        currentLeft.setText("-1");
        currentLeft.commit();
        assertEquals(12f, relation(document.document().root, "child").cell.padLeft, 0f);
        assertEquals(historyBeforeInvalid, document.editSession().historySize());
    }

    @Test
    public void cellRangeShowsConciseSelectionWithoutStructureControls() throws Exception {
        HudNode root = new HudNode("root", HudNodeKind.TABLE);
        root.table = HudLayoutAuthoring.newTableLayout(root, 1, 2, false);
        String first = root.table.rows.get(0).cells.get(0).id;
        String second = root.table.rows.get(0).cells.get(1).id;
        HudScreenEditorDocument document = new HudScreenEditorDocument("hud/range", "HUD",
                asset("hud/range"), new HudDocumentV1(root));
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudPanelTestSupport.selectCell(session, document, first);
        session.selectCellRange(second);
        HudInspectorView view = new HudInspectorView(session);
        assertTrue(labels(view).contains("Selection:"));
        assertTrue(labels(view).contains("2 cells in row"));
        assertNull(view.findActor("hudMergeCells"));
        assertNull(view.findActor("hudDeleteLogicalColumn"));
        assertNotNull(view.findActor("hudPadLeftField"));
    }

    @Test
    public void numericFocusLossCommitsAndEscapeCancelsThroughTheStage() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode child = new HudNode("child", HudNodeKind.GROUP);
        root.children.add(HudChild.free(child, new HudFreePlacement()));
        HudScreenEditorDocument document = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"),
                new HudDocumentV1(root)));
        document.setSelectedNodeId("child");
        HudInspectorView view = new HudInspectorView(HudPanelTestSupport.projectedSession(document));
        Graphics previousGraphics = Gdx.graphics;
        Gdx.graphics = logicalGraphics(200, 200);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        try {
            stage.getViewport().update(200, 200, true);
            view.setBounds(0f, 0f, 200f, 200f);
            stage.addActor(view);
            SimpleFloatField width = view.findActor("hudWidthField");
            stage.setKeyboardFocus(width);
            width.setText("42");
            stage.setKeyboardFocus(null);
            assertEquals(42f, node(document.document().root, "child").actor.width, 0f);

            SimpleFloatField refreshedWidth = view.findActor("hudWidthField");
            stage.setKeyboardFocus(refreshedWidth);
            refreshedWidth.setText("99");
            assertTrue(stage.keyDown(Input.Keys.ESCAPE));
            assertEquals(42f, node(document.document().root, "child").actor.width, 0f);
            assertEquals("42.0", refreshedWidth.getText());
        } finally {
            stage.dispose();
            Gdx.graphics = previousGraphics;
        }
        HudNode button = new HudNode("button", HudNodeKind.TEXT_BUTTON);
        button.textButton = new HudTextButtonData();
        assertEquals("TEXTBUTTON", HudInspectorView.titleFor(button));
    }

    @Test
    public void labelTextAndStyleEditThroughControlsWithMultilineHistoryAndRefresh() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode label = new HudNode("label-1", HudNodeKind.LABEL);
        label.label = new HudLabelData();
        label.label.text = "Label";
        label.label.styleName = "default";
        root.children.add(HudChild.free(label, new HudFreePlacement()));
        HudScreenEditorDocument document = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"),
                new HudDocumentV1(root)));
        document.setSelectedNodeId("label-1");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudPanelTestSupport.projectLabelResources(session);
        HudInspectorView view = new HudInspectorView(session);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        try {
            stage.getViewport().update(320, 240, true);
            view.setBounds(0f, 0f, 320f, 240f);
            stage.addActor(view);

            SimpleTextArea text = view.findActor("hudTextField");
            stage.setKeyboardFocus(text);
            text.setText("Étiquette\nHUD");
            stage.setKeyboardFocus(null);
            assertEquals("Étiquette\nHUD", node(document.document().root, "label-1").label.text);
            assertEquals(1, document.editSession().historySize());
            assertTrue(document.editSession().undo());
            assertEquals("Label", node(document.document().root, "label-1").label.text);
            assertTrue(document.editSession().redo());
            assertEquals("Étiquette\nHUD", node(document.document().root, "label-1").label.text);

            @SuppressWarnings("rawtypes") VisSelectBox styles = view.findActor("hudLabelStyle");
            int small = -1;
            int builtInDefault = -1;
            for (int index = 0; index < styles.getItems().size; index++) {
                if ("small".equals(styles.getItems().get(index).toString())) small = index;
                if ("Default".equals(styles.getItems().get(index).toString())) {
                    builtInDefault = index;
                }
            }
            assertTrue(small >= 0);
            assertTrue(builtInDefault >= 0);
            styles.setSelectedIndex(small);
            assertEquals("small", node(document.document().root, "label-1").label.styleName);
            assertEquals(2, document.editSession().historySize());
            assertTrue(document.editSession().undo());
            assertEquals("default", node(document.document().root, "label-1").label.styleName);
            assertTrue(document.editSession().redo());
            assertEquals("small", node(document.document().root, "label-1").label.styleName);

            styles.setSelectedIndex(builtInDefault);
            assertNull(node(document.document().root, "label-1").label.styleName);
            assertTrue(document.editSession().undo());
            assertEquals("small", node(document.document().root, "label-1").label.styleName);
            assertTrue(document.editSession().redo());
            assertNull(node(document.document().root, "label-1").label.styleName);

            int history = document.editSession().historySize();
            view.rebuild();
            assertEquals(history, document.editSession().historySize());
        } finally {
            stage.dispose();
        }
    }

    @Test
    public void textraLabelPropertiesPersistWhilePreviewActionRemainsTransient() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode label = new HudNode("textra-label-1", HudNodeKind.TEXTRA_LABEL);
        label.textraLabel = new HudTextraLabelData();
        label.textraLabel.text = "Text";
        label.textraLabel.typingEnabled = true;
        root.children.add(HudChild.free(label, new HudFreePlacement()));
        HudScreenEditorDocument document = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"), new HudDocumentV1(root)));
        document.setSelectedNodeId(label.id);
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudInspectorView view = new HudInspectorView(session);

        assertNotNull(view.findActor("hudTextraLabelText"));
        assertNotNull(view.findActor("hudTextraLabelStyle"));
        assertNotNull(view.findActor("hudTextraLabelFont"));
        VisTextButton preview = view.findActor("hudTextraLabelPreviewTyping");
        assertFalse(preview.isDisabled());
        int history = document.editSession().historySize();
        preview.setChecked(true);
        assertEquals(history, document.editSession().historySize());

        VisCheckBox enabled = view.findActor("hudTextraLabelTypingEnabled");
        enabled.setChecked(false);
        assertFalse(node(document.document().root, label.id).textraLabel.typingEnabled);
        assertEquals(history + 1, document.editSession().historySize());
        view.rebuild();
        assertTrue(((VisTextButton) view.findActor(
                "hudTextraLabelPreviewTyping")).isDisabled());
        assertTrue(document.editSession().undo());
        assertTrue(node(document.document().root, label.id).textraLabel.typingEnabled);
        assertTrue(document.editSession().redo());
        assertFalse(node(document.document().root, label.id).textraLabel.typingEnabled);
    }

    @Test
    public void textButtonTextAndStyleControlsPreserveAutomaticCellAxesAndHistory() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = table("root");
        HudNode button = new HudNode("text-button-1", HudNodeKind.TEXT_BUTTON);
        button.textButton = new HudTextButtonData();
        button.textButton.text = "Button";
        button.textButton.styleName = "default";
        HudCellConstraints cell = new HudCellConstraints();
        TextButton probe = new TextButton("Button",
                com.kotcrab.vis.ui.VisUI.getSkin().get(
                        "default", TextButton.TextButtonStyle.class));
        float explicitWidth = probe.getPrefWidth();
        cell.prefWidth = explicitWidth;
        root.table.rows.get(0).cells.get(0).content = button;
        root.table.rows.get(0).cells.get(0).constraints = cell;
        HudScreenEditorDocument document = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"),
                new HudDocumentV1(root)));
        document.setSelectedNodeId("text-button-1");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudPanelTestSupport.projectLabelResources(session);
        HudInspectorView view = new HudInspectorView(session);
        assertNotNull(view.findActor("hudTextButtonFont"));
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        try {
            stage.getViewport().update(320, 240, true);
            view.setBounds(0f, 0f, 320f, 240f);
            stage.addActor(view);

            VisCheckBox widthAuto = view.findActor("hudPrefWidthAuto");
            HudPanelTestSupport.selectCell(session, document, root.table.rows.get(0).cells.get(0).id);
            view.rebuild();
            widthAuto = view.findActor("hudPrefWidthAuto");
            VisCheckBox heightAuto = view.findActor("hudPrefHeightAuto");
            assertFalse(widthAuto.isChecked());
            assertTrue(heightAuto.isChecked());

            widthAuto.setChecked(true);
            assertNull(relation(document.document().root, "text-button-1").cell.prefWidth);
            assertTrue(document.editSession().undo());
            assertEquals(explicitWidth, relation(document.document().root,
                    "text-button-1").cell.prefWidth, 0f);

            heightAuto = view.findActor("hudPrefHeightAuto");
            heightAuto.setChecked(false);
            assertNotNull(relation(document.document().root,
                    "text-button-1").cell.prefHeight);
            assertTrue(document.editSession().undo());
            assertNull(relation(document.document().root, "text-button-1").cell.prefHeight);

            session.selectNode("text-button-1");
            view.rebuild();

            SimpleTextArea text = view.findActor("hudTextField");
            stage.setKeyboardFocus(text);
            text.setText("Button\nSecond line");
            stage.setKeyboardFocus(null);
            HudChild edited = relation(document.document().root, "text-button-1");
            assertEquals("Button\nSecond line", edited.node.textButton.text);
            assertEquals(explicitWidth, edited.cell.prefWidth, 0f);
            assertNull(edited.cell.prefHeight);
            assertTrue(document.editSession().undo());
            assertEquals("Button", relation(document.document().root,
                    "text-button-1").node.textButton.text);
            assertEquals(explicitWidth, relation(document.document().root,
                    "text-button-1").cell.prefWidth, 0f);
            assertTrue(document.editSession().redo());

            @SuppressWarnings("rawtypes") VisSelectBox styles =
                    view.findActor("hudTextButtonStyle");
            int builtInDefault = -1;
            for (int index = 0; index < styles.getItems().size; index++) {
                if ("Default".equals(styles.getItems().get(index).toString())) {
                    builtInDefault = index;
                }
            }
            assertTrue(builtInDefault >= 0);
            styles.setSelectedIndex(builtInDefault);
            assertNull(relation(document.document().root,
                    "text-button-1").node.textButton.styleName);
            assertEquals(explicitWidth, relation(document.document().root,
                    "text-button-1").cell.prefWidth, 0f);
            assertTrue(document.editSession().undo());
            assertEquals("default", relation(document.document().root,
                    "text-button-1").node.textButton.styleName);

            HudDocumentV1 restored = new HudDocumentCodec().read(
                    new HudDocumentCodec().write(document.document()));
            HudChild restoredButton = relation(restored.root, "text-button-1");
            assertEquals("Button\nSecond line", restoredButton.node.textButton.text);
            assertEquals("default", restoredButton.node.textButton.styleName);
            assertEquals(explicitWidth, restoredButton.cell.prefWidth, 0f);
            assertNull(restoredButton.cell.prefHeight);
        } finally {
            stage.dispose();
        }
    }

    @Test
    public void imageTextButtonImageChoicesRecoverLocallyFromUnavailableResources() throws Exception {
        FileHandle rootDirectory = new FileHandle(temporary.newFolder());
        AssetMetaDatabase database = new AssetMetaDatabase();
        AssetMeta unavailable = database.registerIfAbsent(AssetType.IMAGE,
                "images/gone", "orig/images/gone__a1.png", AssetMeta.AssetScope.USER);
        AssetMeta available = database.registerIfAbsent(AssetType.IMAGE,
                "images/available", "orig/images/available__a2.png", AssetMeta.AssetScope.USER);
        rootDirectory.child(unavailable.sourceRelPath()).writeString("source", false);
        rootDirectory.child(available.sourceRelPath()).writeString("source", false);
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode button = new HudNode("image-text-button", HudNodeKind.IMAGE_TEXT_BUTTON);
        button.imageTextButton = new HudImageTextButtonData();
        button.imageTextButton.text = "Button";
        button.imageTextButton.imageOver = image(HudImageSource.DRAWABLE, "legacy-drawable");
        root.children.add(HudChild.free(button, new HudFreePlacement()));
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument document = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"), new HudDocumentV1(root)));
        document.setSelectedNodeId("image-text-button");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document,
                new HudEditorSession(() -> database));
        HudPanelTestSupport.projectDirectory(session, rootDirectory);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudPanelTestSupport.projectHistoryNavigation(session, document);
        HudPanelTestSupport.bindResourceValidatingPreview(session, document, new HudResourceCatalog() {
            @Override public boolean hasRegion(String name) { return "available__a2".equals(name); }
            @Override public boolean hasDrawable(String name) { return "legacy-drawable".equals(name); }
            @Override public boolean hasLabelStyle(String name) { return false; }
            @Override public boolean hasTextButtonStyle(String name) { return false; }
            @Override public boolean hasBuiltInImageTextButtonStyle() { return true; }
        });
        HudInspectorView view = new HudInspectorView(session);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        try {
            stage.getViewport().update(360, 280, true);
            view.setBounds(0f, 0f, 360f, 280f);
            stage.addActor(view);

            @SuppressWarnings("rawtypes") VisSelectBox over =
                    view.findActor("hudImageTextButtonImageOver");
            assertEquals("legacy-drawable", over.getSelected().toString());
            view.rebuild();
            over = view.findActor("hudImageTextButtonImageOver");
            assertEquals("legacy-drawable", over.getSelected().toString());
            assertEquals(HudImageSource.DRAWABLE, node(document.document().root,
                    "image-text-button").imageTextButton.imageOver.source);

            @SuppressWarnings("rawtypes") VisSelectBox up =
                    view.findActor("hudImageTextButtonImageUp");
            up.setSelectedIndex(choiceIndex(up, "images/gone"));
            assertNotNull(view.findActor("hudImageTextButtonImageUpError"));
            up = view.findActor("hudImageTextButtonImageUp");
            assertEquals("Style value", up.getSelected().toString());
            assertNull(node(document.document().root, "image-text-button").imageTextButton.imageUp);
            assertEquals(0, document.editSession().historySize());

            up.setSelectedIndex(choiceIndex(up, "images/available"));
            assertNull(view.findActor("hudImageTextButtonImageUpError"));
            assertEquals("available__a2", node(document.document().root,
                    "image-text-button").imageTextButton.imageUp.resourceName);
            assertEquals(1, document.editSession().historySize());

            up = view.findActor("hudImageTextButtonImageUp");
            up.setSelectedIndex(0);
            assertNull(node(document.document().root, "image-text-button").imageTextButton.imageUp);
            assertEquals(2, document.editSession().historySize());

            up = view.findActor("hudImageTextButtonImageUp");
            up.setSelectedIndex(choiceIndex(up, "images/gone"));
            assertNotNull(view.findActor("hudImageTextButtonImageUpError"));
            HudNode otherRoot = new HudNode("root", HudNodeKind.GROUP);
            HudNode otherButton = new HudNode("image-text-button", HudNodeKind.IMAGE_TEXT_BUTTON);
            otherButton.imageTextButton = new HudImageTextButtonData();
            otherButton.imageTextButton.text = "Button";
            otherRoot.children.add(HudChild.free(otherButton, new HudFreePlacement()));
            HudScreenEditorDocument other = manager.openHudScreen(new HudScreenEditorDocument(
                    "hud/other", "Other", asset("hud/other"), new HudDocumentV1(otherRoot)));
            other.setSelectedNodeId("image-text-button");
            HudPanelTestSupport.projectDocument(session, other);
            view.rebuild();
            assertNull(view.findActor("hudImageTextButtonImageUpError"));
        } finally {
            stage.dispose();
        }
    }

    @Test
    public void imageButtonChoicesUseResourceIdentityAndRebuildWithoutEditing() throws Exception {
        FileHandle rootDirectory = new FileHandle(temporary.newFolder());
        AssetMetaDatabase database = new AssetMetaDatabase();
        AssetMeta imageAsset = database.registerIfAbsent(AssetType.IMAGE,
                "images/readable/icon", "orig/images/icon__a1.png", AssetMeta.AssetScope.USER);
        rootDirectory.child(imageAsset.sourceRelPath()).writeString("source", false);
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode button = new HudNode("image-button", HudNodeKind.IMAGE_BUTTON);
        button.imageButton = new HudImageButtonData();
        button.imageButton.imageUp = image(HudImageSource.REGION, "icon__a1");
        button.imageButton.imageOver = image(HudImageSource.DRAWABLE, "missing-drawable");
        root.children.add(HudChild.free(button, new HudFreePlacement()));
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument document = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"), new HudDocumentV1(root)));
        document.setSelectedNodeId("image-button");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document,
                new HudEditorSession(() -> database));
        HudPanelTestSupport.projectDirectory(session, rootDirectory);
        HudPanelTestSupport.projectLabelResources(session);
        HudInspectorView view = new HudInspectorView(session);

        @SuppressWarnings("rawtypes") VisSelectBox up = view.findActor("hudImageButtonImageUp");
        @SuppressWarnings("rawtypes") VisSelectBox over = view.findActor("hudImageButtonImageOver");
        assertEquals("images/readable/icon", up.getSelected().toString());
        assertEquals("missing-drawable", over.getSelected().toString());
        assertEquals(HudImageSource.DRAWABLE, button.imageButton.imageOver.source);
        assertEquals(0, document.editSession().historySize());

        view.rebuild();
        up = view.findActor("hudImageButtonImageUp");
        over = view.findActor("hudImageButtonImageOver");
        assertEquals("images/readable/icon", up.getSelected().toString());
        assertEquals("missing-drawable", over.getSelected().toString());
        assertEquals(HudImageSource.DRAWABLE, button.imageButton.imageOver.source);
        assertEquals(0, document.editSession().historySize());

        up.setSelectedIndex(0);
        assertNull(node(document.document().root, "image-button").imageButton.imageUp);
        assertEquals(1, document.editSession().historySize());
        assertEquals(imageAsset.id(), database.findByLogicalPath("images/readable/icon").id());
    }

    @Test
    public void textFieldControlsEditAuthoredInitialStateAndPreserveAutomaticSizing() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = table("root");
        HudNode field = new HudNode("text-field-1", HudNodeKind.TEXT_FIELD);
        field.textField = new HudTextFieldData();
        root.table.rows.get(0).cells.get(0).content = field;
        HudScreenEditorDocument document = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"), new HudDocumentV1(root)));
        document.setSelectedNodeId("text-field-1");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudPanelTestSupport.projectLabelResources(session);
        HudInspectorView view = new HudInspectorView(session);
        assertNotNull(view.findActor("hudTextFieldFont"));
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        try {
            stage.getViewport().update(420, 360, true);
            view.setBounds(0f, 0f, 420f, 360f);
            stage.addActor(view);

            assertTitle(view, "TEXTFIELD");
            SimpleTextField text = view.findActor("hudTextFieldText");
            stage.setKeyboardFocus(text);
            text.setText("Ada");
            stage.setKeyboardFocus(null);
            assertEquals("Ada", field(document, "text-field-1").text);

            SimpleTextField placeholder = view.findActor("hudTextFieldPlaceholder");
            stage.setKeyboardFocus(placeholder);
            placeholder.setText("Name");
            stage.setKeyboardFocus(null);
            assertEquals("Name", field(document, "text-field-1").messageText);

            SimpleTextField maxLength = view.findActor("hudTextFieldMaxLength");
            stage.setKeyboardFocus(maxLength);
            maxLength.setText("12");
            stage.setKeyboardFocus(null);
            assertEquals(12, field(document, "text-field-1").maxLength);

            VisCheckBox password = view.findActor("hudTextFieldPassword");
            password.setChecked(true);
            assertTrue(field(document, "text-field-1").passwordMode);
            assertNull(relation(document.document().root, "text-field-1").cell.prefWidth);
            assertNull(relation(document.document().root, "text-field-1").cell.prefHeight);
            assertTrue(document.editSession().undo());
            assertFalse(field(document, "text-field-1").passwordMode);

            HudDocumentV1 restored = new HudDocumentCodec().read(
                    new HudDocumentCodec().write(document.document()));
            HudTextFieldData restoredField = relation(restored.root, "text-field-1").node.textField;
            assertEquals("Ada", restoredField.text);
            assertEquals("Name", restoredField.messageText);
            assertEquals(12, restoredField.maxLength);
        } finally {
            stage.dispose();
        }
    }

    @Test
    public void pendingTextFieldEditCannotCommitIntoAnotherHudWithTheSameNodeId() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument first = manager.openHudScreen(textFieldDocument("hud/first", "First"));
        first.setSelectedNodeId("text-field-1");
        HudEditorSession session = HudPanelTestSupport.projectedSession(first);
        HudPanelTestSupport.projectLabelResources(session);
        HudInspectorView view = new HudInspectorView(session);
        SimpleTextField pending = view.findActor("hudTextFieldText");
        pending.setText("Must not cross documents");

        HudScreenEditorDocument second = manager.openHudScreen(textFieldDocument("hud/second", "Second"));
        second.setSelectedNodeId("text-field-1");
        HudPanelTestSupport.projectDocument(session, second);
        pending.commit();

        assertEquals("First", field(first, "text-field-1").text);
        assertEquals("Second", field(second, "text-field-1").text);
        assertEquals(0, first.editSession().historySize());
        assertEquals(0, second.editSession().historySize());
    }

    @Test
    public void labelTextAreaKeepsEnterForNewlinesAndDeleteForTextEditing() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode label = new HudNode("label-1", HudNodeKind.LABEL);
        label.label = new HudLabelData();
        label.label.text = "Label";
        label.label.styleName = "default";
        root.children.add(HudChild.free(label, new HudFreePlacement()));
        HudScreenEditorDocument document = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"),
                new HudDocumentV1(root)));
        document.setSelectedNodeId("label-1");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudPanelTestSupport.projectLabelResources(session);
        HudInspectorView view = new HudInspectorView(session);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        try {
            stage.getViewport().update(320, 240, true);
            view.setBounds(0f, 0f, 320f, 240f);
            stage.addActor(view);
            SimpleTextArea text = view.findActor("hudTextField");
            stage.setKeyboardFocus(text);
            text.setCursorPosition(text.getText().length());

            stage.keyDown(Input.Keys.ENTER);
            stage.keyTyped('\n');
            assertTrue(text.getText().contains("\n"));
            int beforeDelete = text.getText().length();
            stage.keyDown(Input.Keys.DEL);
            stage.keyTyped('\b');
            assertTrue(text.getText().length() < beforeDelete);
            assertEquals("label-1", session.selectedNodeId());
            assertEquals(HudNodeKind.LABEL,
                    node(document.document().root, "label-1").kind);
        } finally {
            stage.dispose();
        }
    }

    @Test
    public void pendingLabelTextCannotCommitIntoAnotherHudWithTheSameNodeId() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument first = manager.openHudScreen(labelDocument("hud/first", "First"));
        first.setSelectedNodeId("label-1");
        HudEditorSession session = HudPanelTestSupport.projectedSession(first);
        HudPanelTestSupport.projectLabelResources(session);
        HudInspectorView view = new HudInspectorView(session);
        SimpleTextArea pending = view.findActor("hudTextField");
        pending.setText("Must not cross documents");

        HudScreenEditorDocument second = manager.openHudScreen(labelDocument("hud/second", "Second"));
        second.setSelectedNodeId("label-1");
        HudPanelTestSupport.projectDocument(session, second);
        pending.commit();

        assertEquals("First", node(first.document().root, "label-1").label.text);
        assertEquals("Second", node(second.document().root, "label-1").label.text);
        assertEquals(0, first.editSession().historySize());
        assertEquals(0, second.editSession().historySize());
    }

    @Test
    public void selectBoxDuplicateCommittedThroughTheStageShowsADiagnosticWithoutEditing() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument document = manager.openHudScreen(selectBoxDocument("hud/main"));
        document.setSelectedNodeId("select-box-1");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudPanelTestSupport.projectLabelResources(session);
        HudInspectorView view = new HudInspectorView(session);
        assertNotNull(view.findActor("hudSelectBoxFont"));
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        try {
            stage.getViewport().update(320, 240, true);
            view.setBounds(0f, 0f, 320f, 240f);
            stage.addActor(view);
            SimpleTextField value = view.findActor("hudSelectBoxItemText");
            stage.setKeyboardFocus(value);
            value.setText("Second");
            assertTrue(stage.keyDown(Input.Keys.ENTER));

            assertEquals(List.of("First", "Second", "Third"),
                    node(document.document().root, "select-box-1").selectBox.items);
            assertEquals(0, document.editSession().historySize());
            assertNotNull(view.findActor("hudSelectBoxItemError"));
            assertEquals("First", ((SimpleTextField) view.findActor("hudSelectBoxItemText")).getText());

            value = view.findActor("hudSelectBoxItemText");
            stage.setKeyboardFocus(value);
            value.setText("Renamed");
            assertTrue(stage.keyDown(Input.Keys.ENTER));
            assertEquals(List.of("Renamed", "Second", "Third"),
                    node(document.document().root, "select-box-1").selectBox.items);
            assertEquals(1, document.editSession().historySize());
            assertNull(view.findActor("hudSelectBoxItemError"));
        } finally {
            stage.dispose();
        }
    }

    @Test
    public void listInspectorEditsDuplicateRowsAndSelectionAtomicallyWithUndo() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode list = new HudNode("list-1", HudNodeKind.LIST);
        list.list = new HudListData();
        list.list.items.add("Same");
        list.list.items.add("Same");
        list.list.selectedIndex = 1;
        root.children.add(HudChild.free(list, new HudFreePlacement()));
        HudScreenEditorDocument document = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"), new HudDocumentV1(root)));
        document.setSelectedNodeId("list-1");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudPanelTestSupport.projectHistoryNavigation(session, document);
        HudPanelTestSupport.projectLabelResources(session);
        HudInspectorView view = new HudInspectorView(session);

        VisList<String> rows = view.findActor("hudListItems");
        rows.setSelectedIndex(1);
        SimpleTextField text = view.findActor("hudListItemText");
        assertEquals("Same", text.getText());
        text.setText("Changed");
        text.commit();
        assertEquals(List.of("Same", "Changed"), node(document.document().root, "list-1").list.items);
        assertEquals(1, node(document.document().root, "list-1").list.selectedIndex);
        assertTrue(document.editSession().undo());
        assertEquals(List.of("Same", "Same"), node(document.document().root, "list-1").list.items);
        assertTrue(document.editSession().redo());
        assertEquals(List.of("Same", "Changed"), node(document.document().root, "list-1").list.items);
        assertEquals(1, node(document.document().root, "list-1").list.selectedIndex);

        ((VisCheckBox) view.findActor("hudListRequired")).setChecked(false);
        assertFalse(node(document.document().root, "list-1").list.required);
        @SuppressWarnings("unchecked") VisSelectBox<String> selected = view.findActor("hudListSelected");
        assertEquals(2, selected.getSelectedIndex());
        selected.setSelectedIndex(0);
        assertEquals(-1, node(document.document().root, "list-1").list.selectedIndex);
        ((VisCheckBox) view.findActor("hudListRequired")).setChecked(true);
        assertEquals(0, node(document.document().root, "list-1").list.selectedIndex);
    }

    @Test
    public void selectBoxItemEditorKeepsItsActiveValueAcrossRebuildsAndHistory() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument document = manager.openHudScreen(selectBoxDocument("hud/main"));
        document.setSelectedNodeId("select-box-1");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudPanelTestSupport.projectHistoryNavigation(session, document);
        HudPanelTestSupport.projectLabelResources(session);
        HudInspectorView view = new HudInspectorView(session);

        VisList<String> items = view.findActor("hudSelectBoxItems");
        items.setSelectedIndex(1);
        SimpleTextField value = view.findActor("hudSelectBoxItemText");
        assertEquals("Second", value.getText());
        value.setText("Second renamed");
        value.commit();
        assertEquals("Second renamed", ((SimpleTextField) view.findActor("hudSelectBoxItemText")).getText());

        session.editSelectedNode("Move HUD SelectBox item", (node, ignored) -> {
            node.selectBox.items.remove("Second renamed");
            node.selectBox.items.add(0, "Second renamed");
        });
        view.rebuild();
        assertEquals(List.of("Second renamed", "First", "Third"),
                node(document.document().root, "select-box-1").selectBox.items);
        assertEquals("Second renamed", ((SimpleTextField) view.findActor("hudSelectBoxItemText")).getText());

        session.editSelectedNode("Add HUD SelectBox item", (node, ignored) ->
                node.selectBox.items.add("Option 1"));
        view.rebuild();
        assertEquals("Second renamed", ((SimpleTextField) view.findActor("hudSelectBoxItemText")).getText());
        items = view.findActor("hudSelectBoxItems");
        items.setSelectedIndex(3);
        assertEquals("Option 1", ((SimpleTextField) view.findActor("hudSelectBoxItemText")).getText());
        session.editSelectedNode("Remove HUD SelectBox item", (node, ignored) ->
                node.selectBox.items.remove("Option 1"));
        view.rebuild();
        assertEquals("Third", ((SimpleTextField) view.findActor("hudSelectBoxItemText")).getText());
        assertTrue(document.editSession().undo());
        view.rebuild();
        assertEquals("Third", ((SimpleTextField) view.findActor("hudSelectBoxItemText")).getText());
        assertTrue(document.editSession().redo());
        view.rebuild();
        assertEquals("Third", ((SimpleTextField) view.findActor("hudSelectBoxItemText")).getText());
    }

    @Test
    public void pendingSelectBoxItemEditCannotCommitIntoAnotherHudWithTheSameNodeId()
            throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument first = manager.openHudScreen(selectBoxDocument("hud/first"));
        first.setSelectedNodeId("select-box-1");
        HudEditorSession session = HudPanelTestSupport.projectedSession(first);
        HudPanelTestSupport.projectLabelResources(session);
        HudInspectorView view = new HudInspectorView(session);
        SimpleTextField pending = view.findActor("hudSelectBoxItemText");
        pending.setText("Must not cross documents");

        HudScreenEditorDocument second = manager.openHudScreen(selectBoxDocument("hud/second"));
        second.setSelectedNodeId("select-box-1");
        HudPanelTestSupport.projectDocument(session, second);
        pending.commit();

        assertEquals("First", node(first.document().root, "select-box-1").selectBox.items.get(0));
        assertEquals("First", node(second.document().root, "select-box-1").selectBox.items.get(0));
        assertEquals(0, first.editSession().historySize());
        assertEquals(0, second.editSession().historySize());
    }

    @Test
    public void checkBoxPropertiesEditTheAuthoredInitialStateThroughHistory() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode checkBox = new HudNode("check-box-1", HudNodeKind.CHECK_BOX);
        checkBox.checkBox = new HudCheckBoxData();
        checkBox.checkBox.text = "Initial";
        root.children.add(HudChild.free(checkBox, new HudFreePlacement()));
        HudScreenEditorDocument document = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"), new HudDocumentV1(root)));
        document.setSelectedNodeId("check-box-1");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudPanelTestSupport.projectLabelResources(session);
        HudInspectorView view = new HudInspectorView(session);
        assertNotNull(view.findActor("hudCheckBoxFont"));

        assertTitle(view, "CHECKBOX");
        SimpleTextArea text = view.findActor("hudCheckBoxText");
        text.setText("Ready");
        text.commit();
        assertEquals("Ready", node(document.document().root, "check-box-1").checkBox.text);
        VisCheckBox checked = view.findActor("hudCheckBoxChecked");
        checked.setChecked(true);
        assertTrue(node(document.document().root, "check-box-1").checkBox.checked);
        VisCheckBox disabled = view.findActor("hudCheckBoxDisabled");
        disabled.setChecked(true);
        assertTrue(node(document.document().root, "check-box-1").checkBox.disabled);
        assertTrue(document.editSession().undo());
        assertFalse(node(document.document().root, "check-box-1").checkBox.disabled);
    }

    @Test
    public void sliderPropertiesPreserveExactFloatsAcrossDisplayFocusHistoryAndRejection()
            throws Exception {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode slider = new HudNode("slider-1", HudNodeKind.SLIDER);
        slider.slider = new HudSliderData();
        slider.slider.min = -0.125f;
        slider.slider.max = 1.234567f;
        slider.slider.stepSize = 0.125f;
        slider.slider.value = 0.375f;
        root.children.add(HudChild.free(slider, new HudFreePlacement()));
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"), new HudDocumentV1(root));
        document.setSelectedNodeId("slider-1");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudPanelTestSupport.projectHistoryNavigation(session, document);
        HudResourceCatalog catalog = new HudResourceCatalog() {
            @Override public boolean hasRegion(String name) { return false; }
            @Override public boolean hasDrawable(String name) { return false; }
            @Override public boolean hasLabelStyle(String name) { return false; }
            @Override public boolean hasTextButtonStyle(String name) { return false; }
            @Override public boolean hasBuiltInSliderStyle() { return true; }
        };
        HudPanelTestSupport.bindResourceValidatingPreview(session, document, catalog);
        HudInspectorView view = new HudInspectorView(session);

        assertTitle(view, "SLIDER");
        SimpleFloatField min = view.findActor("hudSliderMin");
        SimpleFloatField max = view.findActor("hudSliderMax");
        SimpleFloatField step = view.findActor("hudSliderStep");
        SimpleFloatField value = view.findActor("hudSliderValue");
        assertEquals("-0.125", min.getText());
        assertEquals("1.234567", max.getText());
        assertEquals("0.125", step.getText());
        assertEquals("0.375", value.getText());

        min.commit();
        max.commit();
        step.commit();
        value.commit();
        assertSliderValues(document, -0.125f, 1.234567f, 0.125f, 0.375f);
        assertEquals(0, document.editSession().historySize());

        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        try {
            stage.getViewport().update(320, 240, true);
            view.setBounds(0f, 0f, 320f, 240f);
            stage.addActor(view);
            stage.setKeyboardFocus(min);
            stage.setKeyboardFocus(max);
            stage.setKeyboardFocus(step);
            stage.setKeyboardFocus(value);
            stage.setKeyboardFocus(null);
        } finally {
            stage.dispose();
        }
        assertSliderValues(document, -0.125f, 1.234567f, 0.125f, 0.375f);
        assertEquals(0, document.editSession().historySize());

        value = view.findActor("hudSliderValue");
        value.setText("0.33333334");
        value.commit();
        assertEquals(0.33333334f,
                node(document.document().root, "slider-1").slider.value, 0f);
        assertEquals(1, document.editSession().historySize());
        assertTrue(document.editSession().undo());
        assertEquals(0.375f, node(document.document().root, "slider-1").slider.value, 0f);
        assertTrue(document.editSession().redo());
        assertEquals(0.33333334f,
                node(document.document().root, "slider-1").slider.value, 0f);

        min = view.findActor("hudSliderMin");
        min.setText("0.5");
        min.commit();
        assertEquals(-0.125f, node(document.document().root, "slider-1").slider.min, 0f);
        assertEquals(1, document.editSession().historySize());
        assertNotNull(view.findActor("hudSliderError"));
        assertEquals("-0.125", ((SimpleFloatField) view.findActor("hudSliderMin")).getText());

        @SuppressWarnings("rawtypes") VisSelectBox orientation =
                view.findActor("hudSliderOrientation");
        orientation.setSelectedIndex(1);
        assertEquals(HudSliderOrientation.VERTICAL,
                node(document.document().root, "slider-1").slider.orientation);
        VisCheckBox disabled = view.findActor("hudSliderDisabled");
        disabled.setChecked(true);
        assertTrue(node(document.document().root, "slider-1").slider.disabled);
        assertTrue(document.editSession().undo());
        assertFalse(node(document.document().root, "slider-1").slider.disabled);
        assertTrue(document.editSession().redo());
        assertTrue(node(document.document().root, "slider-1").slider.disabled);
    }

    @Test
    public void progressBarPropertiesUseExactFloatsAndRejectInvalidEditsAtomically() throws Exception {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode progress = new HudNode("progress-1", HudNodeKind.PROGRESS_BAR);
        progress.progressBar = new HudProgressBarData();
        progress.progressBar.stepSize = .125f;
        root.children.add(HudChild.free(progress, new HudFreePlacement()));
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"), new HudDocumentV1(root));
        document.setSelectedNodeId("progress-1");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudPanelTestSupport.projectHistoryNavigation(session, document);
        HudResourceCatalog catalog = new HudResourceCatalog() {
            @Override public boolean hasRegion(String name) { return false; }
            @Override public boolean hasDrawable(String name) { return false; }
            @Override public boolean hasLabelStyle(String name) { return false; }
            @Override public boolean hasTextButtonStyle(String name) { return false; }
            @Override public boolean hasBuiltInProgressBarStyle() { return true; }
        };
        HudPanelTestSupport.bindResourceValidatingPreview(session, document, catalog);
        HudInspectorView view = new HudInspectorView(session);

        assertTitle(view, "PROGRESS BAR");
        SimpleFloatField step = view.findActor("hudProgressBarStep");
        assertEquals("0.125", step.getText());
        step.commit();
        assertEquals(0, document.editSession().historySize());
        SimpleFloatField min = view.findActor("hudProgressBarMin");
        min.setText("75");
        min.commit();
        assertEquals(0f, node(document.document().root, "progress-1").progressBar.min, 0f);
        assertEquals(0, document.editSession().historySize());
        assertNotNull(view.findActor("hudProgressBarError"));

        @SuppressWarnings("rawtypes") VisSelectBox orientation =
                view.findActor("hudProgressBarOrientation");
        orientation.setSelectedIndex(1);
        assertEquals(HudSliderOrientation.VERTICAL,
                node(document.document().root, "progress-1").progressBar.orientation);
    }

    @Test
    public void scrollPanePropertiesRejectAtomicallyRestoreControlsAndRecover() throws Exception {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode pane = new HudNode("scroll-pane-1", HudNodeKind.SCROLL_PANE);
        pane.scrollPane = new HudScrollPaneData();
        root.children.add(HudChild.free(pane, new HudFreePlacement()));
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"), new HudDocumentV1(root));
        document.setSelectedNodeId("scroll-pane-1");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudPanelTestSupport.projectHistoryNavigation(session, document);
        boolean[] defaultStyleAvailable = {true};
        HudResourceCatalog catalog = new HudResourceCatalog() {
            @Override public boolean hasRegion(String name) { return false; }
            @Override public boolean hasDrawable(String name) { return false; }
            @Override public boolean hasLabelStyle(String name) { return false; }
            @Override public boolean hasTextButtonStyle(String name) { return false; }
            @Override public boolean hasBuiltInScrollPaneStyle() {
                return defaultStyleAvailable[0];
            }
        };
        HudPanelTestSupport.bindResourceValidatingPreview(session, document, catalog);
        HudInspectorView view = new HudInspectorView(session);

        assertTitle(view, "SCROLL PANE");
        assertNotNull(view.findActor("hudScrollPaneStyle"));
        assertTrue(((VisCheckBox) view.findActor("hudScrollPaneFade")).isChecked());
        assertTrue(((VisCheckBox) view.findActor("hudScrollPaneFlick")).isChecked());
        assertTrue(((VisCheckBox) view.findActor("hudScrollPaneSmooth")).isChecked());
        assertTrue(((VisCheckBox) view.findActor("hudScrollPaneOverscrollX")).isChecked());
        assertTrue(((VisCheckBox) view.findActor("hudScrollPaneOverscrollY")).isChecked());

        defaultStyleAvailable[0] = false;
        ((VisCheckBox) view.findActor("hudScrollPaneDisabledX")).setChecked(true);
        assertFalse(node(document.document().root, "scroll-pane-1")
                .scrollPane.scrollingDisabledX);
        assertEquals(0, document.editSession().historySize());
        assertNotNull(view.findActor("hudScrollPaneError"));
        assertFalse(((VisCheckBox) view.findActor("hudScrollPaneDisabledX")).isChecked());

        defaultStyleAvailable[0] = true;
        ((VisCheckBox) view.findActor("hudScrollPaneDisabledX")).setChecked(true);
        assertTrue(node(document.document().root, "scroll-pane-1")
                .scrollPane.scrollingDisabledX);
        assertEquals(1, document.editSession().historySize());
        assertNull(view.findActor("hudScrollPaneError"));
        assertTrue(document.editSession().undo());
        assertFalse(node(document.document().root, "scroll-pane-1")
                .scrollPane.scrollingDisabledX);
        assertTrue(document.editSession().redo());
        assertTrue(node(document.document().root, "scroll-pane-1")
                .scrollPane.scrollingDisabledX);
    }

    @Test public void scrollPaneStyleSelectorRejectsStaleChoiceAndClearsTargetedDiagnostic()
            throws Exception {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode pane = new HudNode("pane", HudNodeKind.SCROLL_PANE);
        pane.scrollPane = new HudScrollPaneData();
        root.children.add(HudChild.free(pane, new HudFreePlacement()));
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/first", "First", asset("hud/first"), new HudDocumentV1(root));
        document.setSelectedNodeId("pane");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        Skin skin = new Skin();
        skin.add("gone", new ScrollPane.ScrollPaneStyle());
        skin.add("valid", new ScrollPane.ScrollPaneStyle());
        HudPanelTestSupport.projectLabelResources(session, skin);
        HudPanelTestSupport.projectHistoryNavigation(session, document);
        boolean[] defaultStyleAvailable = {true};
        HudResourceCatalog catalog = new HudResourceCatalog() {
            @Override public boolean hasRegion(String name) { return false; }
            @Override public boolean hasDrawable(String name) { return false; }
            @Override public boolean hasLabelStyle(String name) { return false; }
            @Override public boolean hasTextButtonStyle(String name) { return false; }
            @Override public boolean hasBuiltInScrollPaneStyle() {
                return defaultStyleAvailable[0];
            }
            @Override public boolean hasScrollPaneStyle(String name) {
                return skin.optional(name, ScrollPane.ScrollPaneStyle.class) != null;
            }
        };
        HudPanelTestSupport.bindResourceValidatingPreview(session, document, catalog);
        HudInspectorView view = new HudInspectorView(session);
        @SuppressWarnings("rawtypes") VisSelectBox style = view.findActor("hudScrollPaneStyle");

        skin.remove("gone", ScrollPane.ScrollPaneStyle.class);
        style.setSelectedIndex(choiceIndex(style, "gone"));
        assertNull(node(document.document().root, "pane").scrollPane.styleName);
        assertEquals(0, document.editSession().historySize());
        assertNotNull(view.findActor("hudScrollPaneError"));
        style = view.findActor("hudScrollPaneStyle");
        assertEquals("Default", style.getSelected().toString());

        style.setSelectedIndex(choiceIndex(style, "valid"));
        assertEquals("valid", node(document.document().root, "pane").scrollPane.styleName);
        assertEquals(1, document.editSession().historySize());
        assertNull(view.findActor("hudScrollPaneError"));
        assertTrue(document.editSession().undo());
        assertNull(node(document.document().root, "pane").scrollPane.styleName);
        assertTrue(document.editSession().redo());
        assertEquals("valid", node(document.document().root, "pane").scrollPane.styleName);

        defaultStyleAvailable[0] = false;
        style = view.findActor("hudScrollPaneStyle");
        style.setSelectedIndex(choiceIndex(style, "Default"));
        assertNotNull(view.findActor("hudScrollPaneError"));
        assertEquals("valid", node(document.document().root, "pane").scrollPane.styleName);
        assertEquals(1, document.editSession().historySize());

        HudNode otherRoot = new HudNode("root", HudNodeKind.GROUP);
        HudNode otherPane = new HudNode("pane", HudNodeKind.SCROLL_PANE);
        otherPane.scrollPane = new HudScrollPaneData();
        otherRoot.children.add(HudChild.free(otherPane, new HudFreePlacement()));
        HudScreenEditorDocument other = new HudScreenEditorDocument(
                "hud/second", "Second", asset("hud/second"), new HudDocumentV1(otherRoot));
        other.setSelectedNodeId("pane");
        HudPanelTestSupport.projectDocument(session, other);
        view.rebuild();
        assertNull(view.findActor("hudScrollPaneError"));
        assertEquals(0, other.editSession().historySize());
        skin.dispose();
    }

    @Test public void tooltipPropertiesAreUndoableAndRestoreAfterStaleStyleRejection()
            throws Exception {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/first", "First", asset("hud/first"), new HudDocumentV1(root));
        document.setSelectedNodeId("root");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        Skin skin = new Skin();
        BitmapFont font = new BitmapFont();
        TextTooltip.TextTooltipStyle style = new TextTooltip.TextTooltipStyle(
                new Label.LabelStyle(font, Color.WHITE), null);
        skin.add("gone", style);
        skin.add("valid", new TextTooltip.TextTooltipStyle(style));
        HudPanelTestSupport.projectLabelResources(session, skin);
        HudResourceCatalog catalog = new HudResourceCatalog() {
            @Override public boolean hasRegion(String name) { return false; }
            @Override public boolean hasDrawable(String name) { return false; }
            @Override public boolean hasLabelStyle(String name) { return false; }
            @Override public boolean hasTextButtonStyle(String name) { return false; }
            @Override public boolean hasBuiltInTextTooltipStyle() { return true; }
            @Override public boolean hasTextTooltipStyle(String name, boolean hasFontOverride) {
                return skin.optional(name, TextTooltip.TextTooltipStyle.class) != null;
            }
        };
        HudPanelTestSupport.bindResourceValidatingPreview(session, document, catalog);
        HudInspectorView view = new HudInspectorView(session);

        ((VisCheckBox) view.findActor("hudTooltipEnabled")).setChecked(true);
        assertNotNull(document.document().root.tooltip);
        assertEquals(1, document.editSession().historySize());
        SimpleTextArea text = view.findActor("hudTooltipText");
        text.setText("First line\nSecond line");
        text.commit();
        assertEquals("First line\nSecond line", document.document().root.tooltip.text);
        assertEquals(2, document.editSession().historySize());

        @SuppressWarnings("rawtypes") VisSelectBox styleBox = view.findActor("hudTooltipStyle");
        skin.remove("gone", TextTooltip.TextTooltipStyle.class);
        styleBox.setSelectedIndex(choiceIndex(styleBox, "gone"));
        assertNull(document.document().root.tooltip.styleName);
        assertEquals(2, document.editSession().historySize());
        assertNotNull(view.findActor("hudTooltipStyleError"));
        styleBox = view.findActor("hudTooltipStyle");
        assertEquals("Default", styleBox.getSelected().toString());
        styleBox.setSelectedIndex(choiceIndex(styleBox, "valid"));
        assertEquals("valid", document.document().root.tooltip.styleName);
        assertNull(view.findActor("hudTooltipStyleError"));
        assertTrue(document.editSession().undo());
        assertNull(document.document().root.tooltip.styleName);
        assertTrue(document.editSession().redo());
        assertEquals("valid", document.document().root.tooltip.styleName);

        HudScreenEditorDocument other = new HudScreenEditorDocument(
                "hud/other", "Other", asset("hud/other"),
                new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP)));
        other.setSelectedNodeId("root");
        HudPanelTestSupport.projectDocument(session, other);
        view.rebuild();
        assertNull(view.findActor("hudTooltipStyleError"));
        skin.dispose();
        font.dispose();
    }

    @Test public void windowPropertiesRejectStaleStyleAndKeepHistoryAndDocumentIsolated()
            throws Exception {
        HudNode root = new HudNode("window", HudNodeKind.WINDOW);
        root.window = new HudWindowData();
        root.table = HudLayoutAuthoring.newTableLayout(root, 1, 1, false);
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/first", "First", asset("hud/first"), new HudDocumentV1(root));
        document.setSelectedNodeId("window");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        Skin skin = new Skin();
        BitmapFont font = new BitmapFont();
        skin.add("gone", new Window.WindowStyle(font, Color.WHITE, null));
        skin.add("valid", new Window.WindowStyle(font, Color.WHITE, null));
        HudPanelTestSupport.projectLabelResources(session, skin);
        HudResourceCatalog catalog = new HudResourceCatalog() {
            @Override public boolean hasRegion(String name) { return false; }
            @Override public boolean hasDrawable(String name) { return false; }
            @Override public boolean hasLabelStyle(String name) { return false; }
            @Override public boolean hasTextButtonStyle(String name) { return false; }
            @Override public boolean hasBuiltInWindowStyle() { return true; }
            @Override public boolean hasWindowStyle(String name, boolean hasFontOverride) {
                return skin.optional(name, Window.WindowStyle.class) != null;
            }
        };
        HudPanelTestSupport.bindResourceValidatingPreview(session, document, catalog);
        HudInspectorView view = new HudInspectorView(session);
        assertEquals("WINDOW", HudInspectorView.titleFor(root));
        assertNotNull(view.findActor("hudVisible"));

        SimpleTextField title = view.findActor("hudWindowTitle");
        title.setText("Inventory");
        title.commit();
        assertEquals("Inventory", document.document().root.window.title);
        assertEquals(1, document.editSession().historySize());
        ((VisCheckBox) view.findActor("hudWindowModal")).setChecked(true);
        assertTrue(document.document().root.window.modal);
        assertEquals(2, document.editSession().historySize());

        @SuppressWarnings("rawtypes") VisSelectBox styles = view.findActor("hudWindowStyle");
        skin.remove("gone", Window.WindowStyle.class);
        styles.setSelectedIndex(choiceIndex(styles, "gone"));
        assertNull(document.document().root.window.styleName);
        assertEquals(2, document.editSession().historySize());
        assertNotNull(view.findActor("hudWindowError"));
        styles = view.findActor("hudWindowStyle");
        assertEquals("Default", styles.getSelected().toString());
        styles.setSelectedIndex(choiceIndex(styles, "valid"));
        assertEquals("valid", document.document().root.window.styleName);
        assertNull(view.findActor("hudWindowError"));
        assertTrue(document.editSession().undo());
        assertNull(document.document().root.window.styleName);
        assertTrue(document.editSession().redo());
        assertEquals("valid", document.document().root.window.styleName);

        HudScreenEditorDocument other = new HudScreenEditorDocument(
                "hud/other", "Other", asset("hud/other"),
                new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP)));
        other.setSelectedNodeId("root");
        HudPanelTestSupport.projectDocument(session, other);
        view.rebuild();
        assertNull(view.findActor("hudWindowError"));
        assertEquals(0, other.editSession().historySize());
        skin.dispose();
        font.dispose();
    }

    @Test public void dialogUsesWindowInspectorAndRestoresRejectedStyle() throws Exception {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode dialog = new HudNode("dialog", HudNodeKind.DIALOG);
        dialog.dialog = new HudDialogData();
        dialog.table = HudLayoutAuthoring.newTableLayout(root, 1, 1, false);
        root.children.add(HudChild.free(dialog, new HudFreePlacement()));
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/dialog", "Dialog", asset("hud/dialog"), new HudDocumentV1(root));
        document.setSelectedNodeId("dialog");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        Skin skin = new Skin();
        BitmapFont font = new BitmapFont();
        skin.add("gone", new Window.WindowStyle(font, Color.WHITE, null));
        HudPanelTestSupport.projectLabelResources(session, skin);
        HudResourceCatalog catalog = new HudResourceCatalog() {
            @Override public boolean hasRegion(String name) { return false; }
            @Override public boolean hasDrawable(String name) { return false; }
            @Override public boolean hasLabelStyle(String name) { return false; }
            @Override public boolean hasTextButtonStyle(String name) { return false; }
            @Override public boolean hasBuiltInWindowStyle() { return true; }
            @Override public boolean hasWindowStyle(String name, boolean hasFontOverride) {
                return skin.optional(name, Window.WindowStyle.class) != null;
            }
        };
        HudPanelTestSupport.bindResourceValidatingPreview(session, document, catalog);
        HudInspectorView view = new HudInspectorView(session);
        assertEquals("DIALOG", HudInspectorView.titleFor(dialog));
        assertNull(view.findActor("hudVisible"));
        assertFalse(labels(view).contains("Visible:"));
        SimpleTextField title = view.findActor("hudWindowTitle");
        title.setText("Settings");
        title.commit();
        assertEquals("Settings", dialogNode(document).dialog.title);
        assertTrue(dialogNode(document).dialog.modal);
        @SuppressWarnings("rawtypes") VisSelectBox styles = view.findActor("hudWindowStyle");
        skin.remove("gone", Window.WindowStyle.class);
        styles.setSelectedIndex(choiceIndex(styles, "gone"));
        assertNull(dialogNode(document).dialog.styleName);
        assertNotNull(view.findActor("hudWindowError"));
        assertTrue(document.editSession().undo());
        assertEquals("Dialog", dialogNode(document).dialog.title);
        assertTrue(document.editSession().redo());
        assertEquals("Settings", dialogNode(document).dialog.title);
        skin.dispose();
        font.dispose();
    }

    private static HudNode dialogNode(HudScreenEditorDocument document) {
        return document.document().root.children.get(0).node;
    }

    @Test public void buttonInspectorAssociatesDialogAndKeepsUndoRedo() throws Exception {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode dialog = new HudNode("dialog", HudNodeKind.DIALOG);
        dialog.dialog = new HudDialogData();
        dialog.table = HudLayoutAuthoring.newTableLayout(root, 1, 1, false);
        root.children.add(HudChild.free(dialog, new HudFreePlacement()));
        HudNode button = new HudNode("button", HudNodeKind.TEXT_BUTTON);
        button.textButton = new games.pixscape.runtime.hud.document.HudTextButtonData();
        button.textButton.text = "Open";
        root.children.add(HudChild.free(button, new HudFreePlacement()));
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/action", "Action", asset("hud/action"), new HudDocumentV1(root));
        document.setSelectedNodeId("button");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudInspectorView view = new HudInspectorView(session);
        ((VisTextButton) view.findActor("hudAddWindowAction")).setChecked(true);
        assertEquals("dialog", HudLayoutAuthoring.node(document.document(), "button")
                .windowActions.get(0).targetId);
        view.rebuild();
        @SuppressWarnings("rawtypes") VisSelectBox kinds = view.findActor("hudWindowActionKind0");
        kinds.setSelectedIndex(1);
        assertEquals(HudWindowActionKind.HIDE,
                HudLayoutAuthoring.node(document.document(), "button").windowActions.get(0).action);
        assertTrue(document.editSession().undo());
        assertEquals(HudWindowActionKind.SHOW,
                HudLayoutAuthoring.node(document.document(), "button").windowActions.get(0).action);
        assertTrue(document.editSession().undo());
        assertTrue(HudLayoutAuthoring.node(document.document(), "button").windowActions.isEmpty());
        assertTrue(document.editSession().redo());
        assertEquals("dialog", HudLayoutAuthoring.node(document.document(), "button")
                .windowActions.get(0).targetId);
    }

    @Test public void commonVisibilityEditsUndoRedoAndReopensWithoutCrossDocumentWrites()
            throws Exception {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/visible", "Visible", asset("hud/visible"), new HudDocumentV1(root));
        document.setSelectedNodeId("root");
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudInspectorView view = new HudInspectorView(session);
        assertTrue(labels(view).contains("Visible:"));
        VisCheckBox visible = view.findActor("hudVisible");
        view.setSize(310f, view.getPrefHeight());
        view.validate();
        Actor width = view.findActor("hudWidthField");
        assertEquals(width.getX(), visible.getX(), 0.01f);
        assertEquals(visible.getPrefWidth(), visible.getWidth(), 0.01f);
        assertTrue(visible.isChecked());
        visible.setChecked(false);
        assertFalse(document.document().root.visible);
        assertEquals(1, document.editSession().historySize());
        assertTrue(document.editSession().undo());
        assertTrue(document.document().root.visible);
        assertTrue(document.editSession().redo());
        assertFalse(document.document().root.visible);

        HudDocumentV1 reopened = new HudDocumentCodec().read(
                new HudDocumentCodec().write(document.document()));
        assertFalse(reopened.root.visible);
        HudScreenEditorDocument other = new HudScreenEditorDocument(
                "hud/other", "Other", asset("hud/other"), reopened);
        other.setSelectedNodeId("root");
        HudPanelTestSupport.projectDocument(session, other);
        view.rebuild();
        assertFalse(((VisCheckBox) view.findActor("hudVisible")).isChecked());
        visible.setChecked(true); // Stale inspector control belongs to the original document.
        assertFalse(other.document().root.visible);
        assertEquals(0, other.editSession().historySize());
        ((VisCheckBox) view.findActor("hudVisible")).setChecked(true);
        assertTrue(other.document().root.visible);
        assertFalse(document.document().root.visible);
    }

    private static void assertSliderValues(HudScreenEditorDocument document,
                                           float min, float max, float step, float value) {
        HudSliderData slider = node(document.document().root, "slider-1").slider;
        assertEquals(min, slider.min, 0f);
        assertEquals(max, slider.max, 0f);
        assertEquals(step, slider.stepSize, 0f);
        assertEquals(value, slider.value, 0f);
    }

    private static void assertTitle(HudInspectorView view, String expected) {
        Actor title = view.getChildren().first();
        assertEquals(expected, ((VisLabel) title).getText().toString());
        Cell<Actor> cell = view.getCell(title);
        assertEquals(Align.center, cell.getAlign().intValue());
        assertEquals(CommonLayout.PROPERTY_SECTION_TITLE_BOTTOM_PAD,
                cell.getPadBottomValue().get(view), 0f);
    }

    private static List<String> labels(HudInspectorView view) {
        List<String> labels = new ArrayList<>();
        for (Actor actor : view.getChildren()) {
            if (actor instanceof VisLabel label) labels.add(label.getText().toString());
        }
        return labels;
    }

    private static int choiceIndex(VisSelectBox<?> box, String label) {
        for (int index = 0; index < box.getItems().size; index++) {
            if (label.equals(box.getItems().get(index).toString())) return index;
        }
        throw new AssertionError("Missing choice: " + label);
    }

    private static HudNode node(HudNode root, String id) {
        if (root == null) return null;
        if (id.equals(root.id)) return root;
        for (HudChild child : root.children) {
            HudNode found = node(child.node, id);
            if (found != null) return found;
        }
        if (root.table != null) for (HudTableRow row : root.table.rows)
            for (HudTableCell cell : row.cells) if (cell.content != null) {
                HudNode found = node(cell.content, id);
                if (found != null) return found;
            }
        return null;
    }

    private static HudChild relation(HudNode root, String id) {
        if (root == null) return null;
        for (HudChild child : root.children) {
            if (id.equals(child.node.id)) return child;
            HudChild found = relation(child.node, id);
            if (found != null) return found;
        }
        if (root.table != null) for (HudTableRow row : root.table.rows)
            for (HudTableCell cell : row.cells) if (cell.content != null) {
                if (id.equals(cell.content.id)) return HudChild.cell(cell.content, cell.constraints);
                HudChild found = relation(cell.content, id);
                if (found != null) return found;
            }
        return null;
    }

    private static HudNode table(String id) {
        HudNode node = new HudNode(id, HudNodeKind.TABLE);
        node.table = HudLayoutAuthoring.newTableLayout(node, 1, 1, false);
        return node;
    }

    private static HudTextFieldData field(HudScreenEditorDocument document, String id) {
        return relation(document.document().root, id).node.textField;
    }

    private static HudScreenEditorDocument labelDocument(String id, String text) {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode label = new HudNode("label-1", HudNodeKind.LABEL);
        label.label = new HudLabelData();
        label.label.text = text;
        label.label.styleName = "default";
        root.children.add(HudChild.free(label, new HudFreePlacement()));
        return new HudScreenEditorDocument(id, id,
                asset(id), new HudDocumentV1(root));
    }

    private static HudScreenEditorDocument textFieldDocument(String id, String text) {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode field = new HudNode("text-field-1", HudNodeKind.TEXT_FIELD);
        field.textField = new HudTextFieldData();
        field.textField.text = text;
        root.children.add(HudChild.free(field, new HudFreePlacement()));
        return new HudScreenEditorDocument(id, id, asset(id), new HudDocumentV1(root));
    }

    private static HudScreenEditorDocument selectBoxDocument(String id) {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode selectBox = new HudNode("select-box-1", HudNodeKind.SELECT_BOX);
        selectBox.selectBox = new HudSelectBoxData();
        selectBox.selectBox.items.add("First");
        selectBox.selectBox.items.add("Second");
        selectBox.selectBox.items.add("Third");
        selectBox.selectBox.selectedIndex = 0;
        root.children.add(HudChild.free(selectBox, new HudFreePlacement()));
        return new HudScreenEditorDocument(id, id, asset(id), new HudDocumentV1(root));
    }

    private static HudImageData image(HudImageSource source, String resourceName) {
        HudImageData image = new HudImageData();
        image.source = source;
        image.resourceName = resourceName;
        return image;
    }

    private static games.pixscape.runtime.hud.HudScreenAsset asset(String screenId) {
        games.pixscape.runtime.hud.HudScreenAsset asset = new games.pixscape.runtime.hud.HudScreenAsset();
        asset.documentId = screenId + ".json";
        return asset;
    }

    private static final class MutableLabelCatalog implements HudResourceCatalog {
        private final int firstFontId;
        private final int secondFontId;
        private boolean styleHasFont;

        private MutableLabelCatalog(int firstFontId, int secondFontId) {
            this.firstFontId = firstFontId;
            this.secondFontId = secondFontId;
        }

        @Override public boolean hasRegion(String name) { return false; }
        @Override public boolean hasDrawable(String name) { return false; }
        @Override public boolean hasLabelStyle(String name) { return "fontless".equals(name); }
        @Override public boolean hasLabelStyleFont(String name) {
            return "fontless".equals(name) && styleHasFont;
        }
        @Override public boolean hasBitmapFont(int assetId) {
            return assetId == firstFontId || assetId == secondFontId;
        }
        @Override public boolean hasTextButtonStyle(String name) { return false; }
        @Override public boolean hasTextFieldStyle(String name, boolean hasFontOverride) {
            return "fontless".equals(name) && (hasFontOverride || styleHasFont);
        }
    }

    private static Batch inertBatch() {
        return (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(),
                new Class<?>[]{Batch.class}, (proxy, method, args) -> primitiveDefault(method.getReturnType()));
    }

    private static Graphics logicalGraphics(int width, int height) {
        return (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
                new Class<?>[]{Graphics.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth", "getBackBufferWidth" -> width;
                    case "getHeight", "getBackBufferHeight" -> height;
                    case "getDeltaTime" -> 1f / 60f;
                    default -> primitiveDefault(method.getReturnType());
                });
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == float.class) return 0f;
        return null;
    }
}
