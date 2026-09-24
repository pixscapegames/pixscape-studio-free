package games.pixscape.studio.service.hud;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudLabelData;
import games.pixscape.runtime.hud.document.HudTextraLabelData;
import com.github.tommyettinger.textra.TypingLabel;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.ui.hud.HudInspectorView;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class HudSkinAssignmentIntegrationTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void nativeSkinReplacementRebuildsPreviewAndSupportsUndoRedoAndNone()
            throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("project"));
        FileHandle firstFile = project.child("orig/skins/1/first.json");
        FileHandle secondFile = project.child("orig/skins/2/second.json");
        firstFile.parent().mkdirs();
        secondFile.parent().mkdirs();
        firstFile.writeString("{}", false, "UTF-8");
        secondFile.writeString("{}", false, "UTF-8");
        AssetMetaDatabase database = new AssetMetaDatabase();
        AssetMeta first = database.registerIfAbsent(AssetType.SKIN, "skins/First",
                "orig/skins/1/first.json", AssetMeta.AssetScope.USER);
        AssetMeta second = database.registerIfAbsent(AssetType.SKIN, "skins/Second",
                "orig/skins/2/second.json", AssetMeta.AssetScope.USER);
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/main.json";
        asset.skinId = first.sourceRelPath();
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "Main", asset,
                new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP)));
        document.setSelectedNodeId("root");
        HudEditorSession session = new HudEditorSession(() -> database, () -> inertBatch());
        try {
            session.open(project, document);
            assertEquals(HudEditorSession.Status.READY, session.status());
            Object firstRoot = session.materializedHud().root();

            assertTrue(session.assignSkin("hud/main", second.id()));
            Object secondRoot = session.materializedHud().root();
            assertNotSame(firstRoot, secondRoot);
            assertEquals(second.sourceRelPath(), document.asset().skinId);
            assertEquals("root", session.selectedNodeId());

            assertTrue(document.editSession().undo());
            assertEquals(first.sourceRelPath(), document.asset().skinId);
            assertNotSame(secondRoot, session.materializedHud().root());
            assertTrue(document.editSession().redo());
            assertEquals(second.sourceRelPath(), document.asset().skinId);

            assertTrue(session.assignSkin("hud/main", null));
            assertNull(document.asset().skinId);
            assertEquals(HudEditorSession.Status.READY, session.status());
        } finally {
            session.dispose();
        }
    }

    @Test
    public void textraAuthoringStartsRevealedAndPreviewTypingIsTransient() throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("textra-project"));
        AssetMetaDatabase database = new AssetMetaDatabase();
        HudNode label = new HudNode("textra-label-1", HudNodeKind.TEXTRA_LABEL);
        label.textraLabel = new HudTextraLabelData();
        label.textraLabel.text = "Text";
        label.textraLabel.typingEnabled = true;
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "Main", asset(null), new HudDocumentV1(label));
        document.setSelectedNodeId(label.id);
        HudEditorSession session = new HudEditorSession(() -> database, () -> inertBatch());
        try {
            session.open(project, document);
            assertEquals(HudEditorSession.Status.READY, session.status());
            TypingLabel initial = (TypingLabel) session.materializedHud().actor(label.id);
            assertTrue(initial.hasEnded());

            int history = document.editSession().historySize();
            assertTrue(session.previewSelectedTextraTyping());
            assertFalse(initial.hasEnded());
            assertEquals(history, document.editSession().historySize());

            assertTrue(session.editSelectedNode("Edit Textra text",
                    (node, relation) -> node.textraLabel.text = "Updated"));
            TypingLabel edited = (TypingLabel) session.materializedHud().actor(label.id);
            assertNotSame(initial, edited);
            assertTrue(edited.hasEnded());
            assertEquals("Updated", document.document().root.textraLabel.text);
            assertTrue(document.editSession().undo());
            assertEquals("Text", document.document().root.textraLabel.text);
            assertTrue(((TypingLabel) session.materializedHud().actor(label.id)).hasEnded());
            assertTrue(document.editSession().redo());
            assertEquals("Updated", document.document().root.textraLabel.text);
        } finally {
            session.dispose();
        }
    }

    @Test
    public void incompatibleNativeSkinAndNoneAreRejectedWithoutReplacingLiveResources()
            throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("styled-project"));
        FileHandle firstFile = project.child("orig/skins/1/first.json");
        FileHandle secondFile = project.child("orig/skins/2/second.json");
        FileHandle badFile = project.child("orig/skins/3/bad.json");
        writeLabelSkin(firstFile);
        writeLabelSkin(secondFile);
        badFile.parent().mkdirs();
        badFile.writeString("{}", false, "UTF-8");
        AssetMetaDatabase database = new AssetMetaDatabase();
        AssetMeta first = database.registerIfAbsent(AssetType.SKIN, "skins/First",
                "orig/skins/1/first.json", AssetMeta.AssetScope.USER);
        AssetMeta second = database.registerIfAbsent(AssetType.SKIN, "skins/Second",
                "orig/skins/2/second.json", AssetMeta.AssetScope.USER);
        AssetMeta bad = database.registerIfAbsent(AssetType.SKIN, "skins/Bad",
                "orig/skins/3/bad.json", AssetMeta.AssetScope.USER);
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/main.json";
        asset.skinId = first.sourceRelPath();
        HudNode label = new HudNode("label-1", HudNodeKind.LABEL);
        label.label = new HudLabelData();
        label.label.text = "A";
        label.label.styleName = "body";
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "Main", asset, new HudDocumentV1(label));
        document.setSelectedNodeId("label-1");
        HudEditorSession session = new HudEditorSession(() -> database, () -> inertBatch());
        try {
            session.open(project, document);
            assertEquals(HudEditorSession.Status.READY, session.status());
            assertTrue(session.assignSkin("hud/main", second.id()));
            Object compatibleRoot = session.materializedHud().root();
            assertEquals(java.util.List.of("body"), session.labelStyleNames());
            int history = document.editSession().historySize();

            HudEditRejectedException badFailure = assertThrows(HudEditRejectedException.class,
                    () -> session.assignSkin("hud/main", bad.id()));
            assertTrue(badFailure.getMessage().contains("body"));
            assertEquals(second.sourceRelPath(), document.asset().skinId);
            assertEquals(history, document.editSession().historySize());
            assertSame(compatibleRoot, session.materializedHud().root());
            assertEquals("label-1", session.selectedNodeId());

            HudEditRejectedException noneFailure = assertThrows(HudEditRejectedException.class,
                    () -> session.assignSkin("hud/main", null));
            assertTrue(noneFailure.getMessage().contains("body"));
            assertEquals(second.sourceRelPath(), document.asset().skinId);
            assertEquals(history, document.editSession().historySize());
            assertSame(compatibleRoot, session.materializedHud().root());
        } finally {
            session.dispose();
        }
    }

    @Test
    public void missingSkinOpenRecoversThroughInspectorAndRejectedUndoKeepsValidPreview()
            throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("error-recovery"));
        FileHandle compatibleFile = project.child("orig/skins/1/compatible.json");
        writeLabelSkin(compatibleFile);
        AssetMetaDatabase database = new AssetMetaDatabase();
        AssetMeta compatible = database.registerIfAbsent(AssetType.SKIN, "skins/Compatible",
                "orig/skins/1/compatible.json", AssetMeta.AssetScope.USER);
        HudScreenAsset asset = asset("orig/skins/99/missing.json");
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "Main", asset, styledLabel());
        HudEditorSession session = new HudEditorSession(() -> database, () -> inertBatch());
        try {
            session.open(project, document);
            assertEquals(HudEditorSession.Status.ERROR, session.status());
            assertNull(session.materializedHud());
            assertTrue(session.errorMessage().contains("missing"));
            HudInspectorView inspector = new HudInspectorView(session);

            select(inspector, "Compatible");

            assertEquals(HudEditorSession.Status.READY, session.status());
            assertNull(session.errorMessage());
            assertNotNull(session.materializedHud());
            assertEquals(compatible.sourceRelPath(), document.asset().skinId);
            assertEquals(compatible.sourceRelPath(), session.asset().skinId);
            assertEquals("Compatible", ((VisSelectBox<?>) inspector.findActor(
                    "hudScreenSkin")).getSelected().toString());
            assertEquals(1, document.editSession().historySize());
            assertTrue(document.isDirty());

            Object validRoot = session.materializedHud().root();
            long revision = document.editSession().currentRevision();
            HudEditRejectedException failure = assertThrows(HudEditRejectedException.class,
                    () -> document.editSession().undo());
            assertTrue(failure.getMessage().contains("missing"));
            assertEquals(revision, document.editSession().currentRevision());
            assertEquals(compatible.sourceRelPath(), document.asset().skinId);
            assertEquals(1, document.editSession().historySize());
            assertTrue(document.editSession().canUndo());
            assertSame(validRoot, session.materializedHud().root());
            assertEquals(HudEditorSession.Status.READY, session.status());
        } finally {
            session.dispose();
        }
    }

    @Test
    public void errorStateRejectsIncompatibleSkinAndNoneWithoutPublishing()
            throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("error-rejection"));
        FileHandle badFile = project.child("orig/skins/1/bad.json");
        badFile.parent().mkdirs();
        badFile.writeString("{}", false, "UTF-8");
        AssetMetaDatabase database = new AssetMetaDatabase();
        database.registerIfAbsent(AssetType.SKIN, "skins/Bad",
                "orig/skins/1/bad.json", AssetMeta.AssetScope.USER);
        String missing = "orig/skins/99/missing.json";
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "Main", asset(missing), styledLabel());
        HudEditorSession session = new HudEditorSession(() -> database, () -> inertBatch());
        try {
            session.open(project, document);
            assertEquals(HudEditorSession.Status.ERROR, session.status());
            HudInspectorView inspector = new HudInspectorView(session);

            select(inspector, "Bad");

            assertEquals(missing, document.asset().skinId);
            assertEquals(missing, session.asset().skinId);
            assertEquals(0, document.editSession().historySize());
            assertFalse(document.isDirty());
            assertEquals(HudEditorSession.Status.ERROR, session.status());
            assertNull(session.materializedHud());
            VisLabel diagnostic = inspector.findActor("hudScreenSkinError");
            assertNotNull(diagnostic);
            assertTrue(diagnostic.getText().toString().contains("body"));

            select(inspector, "None");
            assertEquals(missing, document.asset().skinId);
            assertEquals(0, document.editSession().historySize());
            assertFalse(document.isDirty());
            assertEquals(HudEditorSession.Status.ERROR, session.status());
            assertNull(session.materializedHud());
        } finally {
            session.dispose();
        }
    }

    @Test
    public void errorStateAcceptsNoneWhenDocumentHasNoSkinDependency() throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("error-none"));
        AssetMetaDatabase database = new AssetMetaDatabase();
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "Main", asset("orig/skins/99/missing.json"),
                new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP)));
        HudEditorSession session = new HudEditorSession(() -> database, () -> inertBatch());
        try {
            session.open(project, document);
            assertEquals(HudEditorSession.Status.ERROR, session.status());
            HudInspectorView inspector = new HudInspectorView(session);

            select(inspector, "None");

            assertEquals(HudEditorSession.Status.READY, session.status());
            assertNotNull(session.materializedHud());
            assertNull(document.asset().skinId);
            assertNull(session.asset().skinId);
            assertEquals(1, document.editSession().historySize());
        } finally {
            session.dispose();
        }
    }

    @Test
    public void switchingAwayFromFailedOpenDetachesItsPreviewBinding() throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("error-tab-switch"));
        FileHandle compatibleFile = project.child("orig/skins/1/compatible.json");
        compatibleFile.parent().mkdirs();
        compatibleFile.writeString("{}", false, "UTF-8");
        AssetMetaDatabase database = new AssetMetaDatabase();
        AssetMeta compatible = database.registerIfAbsent(AssetType.SKIN, "skins/Compatible",
                "orig/skins/1/compatible.json", AssetMeta.AssetScope.USER);
        HudScreenEditorDocument failed = new HudScreenEditorDocument(
                "hud/failed", "Failed", asset("orig/skins/99/missing.json"),
                new HudDocumentV1(new HudNode("failed-root", HudNodeKind.GROUP)));
        HudScreenEditorDocument active = new HudScreenEditorDocument(
                "hud/active", "Active", asset(null),
                new HudDocumentV1(new HudNode("active-root", HudNodeKind.GROUP)));
        HudEditorSession session = new HudEditorSession(() -> database, () -> inertBatch());
        try {
            session.open(project, failed);
            assertEquals(HudEditorSession.Status.ERROR, session.status());
            session.open(project, active);
            assertEquals(HudEditorSession.Status.READY, session.status());
            Object activeRoot = session.materializedHud().root();

            failed.editSession().editSkin(
                    "Assign HUD Screen Skin", compatible.sourceRelPath());

            assertEquals(compatible.sourceRelPath(), failed.asset().skinId);
            assertNull(active.asset().skinId);
            assertEquals("hud/active", session.screenId());
            assertNull(session.asset().skinId);
            assertSame(activeRoot, session.materializedHud().root());
            assertEquals(0, active.editSession().historySize());
        } finally {
            session.dispose();
        }
    }

    private static HudScreenAsset asset(String skinId) {
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/main.json";
        asset.skinId = skinId;
        return asset;
    }

    private static HudDocumentV1 styledLabel() {
        HudNode label = new HudNode("label-1", HudNodeKind.LABEL);
        label.label = new HudLabelData();
        label.label.text = "A";
        label.label.styleName = "body";
        return new HudDocumentV1(label);
    }

    @SuppressWarnings("rawtypes")
    private static void select(HudInspectorView inspector, String label) {
        VisSelectBox box = inspector.findActor("hudScreenSkin");
        for (int index = 0; index < box.getItems().size; index++) {
            if (label.equals(box.getItems().get(index).toString())) {
                box.setSelectedIndex(index);
                return;
            }
        }
        throw new AssertionError("Missing Skin choice: " + label);
    }

    private static void writeLabelSkin(FileHandle descriptor) {
        descriptor.parent().mkdirs();
        descriptor.writeString("""
                {
                  "com.badlogic.gdx.graphics.Color":{"white":{"r":1,"g":1,"b":1,"a":1}},
                  "com.badlogic.gdx.graphics.g2d.BitmapFont":{"default":{"file":"font.fnt"}},
                  "com.badlogic.gdx.scenes.scene2d.ui.Label$LabelStyle":{"body":{"font":"default","fontColor":"white"}}
                }
                """, false, "UTF-8");
        descriptor.sibling("font.fnt").writeString("""
                info face="test" size=16 bold=0 italic=0 charset="" unicode=0 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=0,0
                common lineHeight=16 base=12 scaleW=2 scaleH=2 pages=1 packed=0
                page id=0 file="font.png"
                chars count=1
                char id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=1 page=0 chnl=0
                kernings count=0
                """, false, "UTF-8");
        Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(1f, 1f, 1f, 1f);
            pixmap.fill();
            PixmapIO.writePNG(descriptor.sibling("font.png"), pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    private static Batch inertBatch() {
        return (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(),
                new Class<?>[]{Batch.class}, (proxy, method, args) -> {
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == float.class) return 0f;
                    return null;
                });
    }
}
