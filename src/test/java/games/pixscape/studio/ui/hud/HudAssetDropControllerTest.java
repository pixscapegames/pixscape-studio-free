package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudPlacementKind;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.service.hud.HudEditorSession;
import games.pixscape.studio.service.hud.HudImageAuthoringService;
import games.pixscape.studio.service.hud.HudLayoutAuthoring;
import games.pixscape.studio.ui.asset.dnd.DragContext;
import games.pixscape.studio.ui.asset.dnd.DragPayload;
import org.junit.Rule;
import org.junit.Test;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class HudAssetDropControllerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static GL20 previousGl;
    private static GL20 previousGl20;

    @BeforeClass public static void bootGdx() {
        if (Gdx.app == null) new HeadlessApplication(new ApplicationAdapter() {},
                new HeadlessApplicationConfiguration());
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        Gdx.gl = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(),
                new Class<?>[]{GL20.class}, (proxy, method, args) -> primitiveDefault(method.getReturnType()));
        Gdx.gl20 = Gdx.gl;
    }

    @AfterClass public static void restoreGl() {
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
    }

    @After public void clearDrag() { DragContext.get().cancel(); }

    @Test public void sharedDragReleaseUsesProjectedTargetSizeBoundsAndSelectionHistory() throws Exception {
        Graphics previousGraphics = Gdx.graphics;
        Gdx.graphics = logicalGraphics(400, 300);
        ActualFixture f = null;
        try {
            f = actualFixture();
            DragPayload payload = imagePayload(f.image.id());
            payload.imageWidth = 20;
            payload.imageHeight = 10;
            DragContext.get().begin(payload);
            f.controller.observeActiveDocument();

            Vector2 projected = f.session.hudViewport().project(new Vector2(95f, 95f));
            DragContext.get().signalRelease();
            f.controller.updateAt(Math.round(projected.x), 300 - Math.round(projected.y), 300, true);

            assertFalse(DragContext.get().active());
            assertTrue(f.failures.isEmpty());
            assertEquals(1, f.hud.editSession().historySize());
            assertEquals(2, f.hud.document().root.children.size());
            var created = f.hud.document().root.children.get(1);
            assertEquals("image-1", created.node.id);
            assertEquals("test__a1", created.node.image.resourceName);
            assertEquals(20f, created.node.actor.width, 0.001f);
            assertEquals(10f, created.node.actor.height, 0.001f);
            assertEquals(80f, created.free.offsetX, 0.001f);
            assertEquals(90f, created.free.offsetY, 0.001f);
            assertEquals("image-1", f.session.selectedNodeId());

            assertTrue(f.hud.editSession().undo());
            assertEquals("root", f.session.selectedNodeId());
            assertEquals(1, f.hud.document().root.children.size());
            assertTrue(f.hud.editSession().redo());
            assertEquals("image-1", f.session.selectedNodeId());
            assertEquals("image-1", f.hud.document().root.children.get(1).node.id);
            assertEquals(80f, f.hud.document().root.children.get(1).free.offsetX, 0.001f);

            DragContext.get().begin(imagePayload(f.image.id()));
            f.controller.observeActiveDocument();
            DragContext.get().signalRelease();
            f.controller.updateAt(5, 5, 300, true);
            assertEquals("outside release must not add history", 1, f.hud.editSession().historySize());

            DragContext.get().begin(imagePayload(f.image.id()));
            f.controller.observeActiveDocument();
            Vector2 existingImage = f.session.hudViewport().project(new Vector2(90f, 95f));
            f.controller.updateAt(Math.round(existingImage.x), 300 - Math.round(existingImage.y),
                    300, true);
            assertTrue("an existing Image is not a replacement destination",
                    f.controller.isShowingForbiddenFeedback());
            DragContext.get().cancel();
            f.controller.updateAt(5, 5, 300, false);

            DragPayload nestedPayload = imagePayload(f.image.id());
            nestedPayload.imageWidth = 20;
            nestedPayload.imageHeight = 10;
            DragContext.get().begin(nestedPayload);
            f.controller.observeActiveDocument();
            Vector2 nested = f.session.hudViewport().project(new Vector2(5f, 5f));
            DragContext.get().signalRelease();
            f.controller.updateAt(Math.round(nested.x), 300 - Math.round(nested.y), 300, true);
            assertEquals(2, f.hud.editSession().historySize());
            assertEquals("image-2", f.hud.document().root.children.get(0).node.children.get(0).node.id);
            assertEquals(5f, f.hud.document().root.children.get(0).node.children.get(0).free.offsetX,
                    0.51f);
            assertEquals(5f, f.hud.document().root.children.get(0).node.children.get(0).free.offsetY,
                    0.51f);
        } finally {
            if (f != null) f.close();
            Gdx.graphics = previousGraphics;
        }
    }

    @Test public void cursorFeedbackTracksTheSameLiveDestinationUsedByDrop() throws Exception {
        Graphics previousGraphics = Gdx.graphics;
        Gdx.graphics = logicalGraphics(400, 300);
        ActualFixture f = null;
        try {
            f = actualFixture();
            DragPayload payload = imagePayload(f.image.id());
            payload.imageWidth = 20;
            payload.imageHeight = 10;
            DragContext.get().begin(payload);
            f.controller.observeActiveDocument();

            f.controller.updateAt(5, 5, 300, true);
            assertTrue(f.controller.isShowingForbiddenFeedback());

            Vector2 valid = f.session.hudViewport().project(new Vector2(75f, 75f));
            f.controller.updateAt(Math.round(valid.x), 300 - Math.round(valid.y), 300, true);
            assertFalse(f.controller.isShowingForbiddenFeedback());

            f.controller.updateAt(Math.round(valid.x), 300 - Math.round(valid.y), 300, false);
            assertTrue(f.controller.isShowingForbiddenFeedback());

            DragContext.get().cancel();
            f.controller.updateAt(5, 5, 300, false);
            assertFalse(f.controller.isShowingForbiddenFeedback());
        } finally {
            if (f != null) f.close();
            Gdx.graphics = previousGraphics;
        }
    }

    @Test public void tableDropsUseNativeCellPreferenceAndRetainOrderThroughHistory() throws Exception {
        Graphics previousGraphics = Gdx.graphics;
        Gdx.graphics = logicalGraphics(400, 300);
        ActualFixture f = null;
        try {
            f = actualTableFixture();
            dropAtHud(f, 10f, 10f);

            var first = cell(f.hud.document().root, 0);
            assertEquals(0f, first.content.actor.width, 0f);
            assertEquals(0f, first.content.actor.height, 0f);
            assertNull(first.constraints.prefWidth);
            assertNull(first.constraints.prefHeight);
            Table table = (Table) f.session.materializedHud().actor("table-root");
            table.validate();
            Image firstActor = (Image) f.session.materializedHud().actor("image-1");
            assertEquals(20f, firstActor.getPrefWidth(), 0.001f);
            assertEquals(10f, firstActor.getPrefHeight(), 0.001f);
            assertEquals(20f, firstActor.getWidth(), 0.001f);
            assertEquals(10f, firstActor.getHeight(), 0.001f);

            dropAtHud(f, 90f, 90f);
            assertEquals("image-1", cell(f.hud.document().root, 0).content.id);
            assertEquals("image-2", cell(f.hud.document().root, 1).content.id);
            assertEquals("test__a1", cell(f.hud.document().root, 0).content.image.resourceName);
            assertEquals("test__a1", cell(f.hud.document().root, 1).content.image.resourceName);
            table = (Table) f.session.materializedHud().actor("table-root");
            table.validate();
            assertEquals(20f, f.session.materializedHud().actor("image-1").getWidth(), 0.001f);
            assertEquals(10f, f.session.materializedHud().actor("image-1").getHeight(), 0.001f);
            assertEquals(20f, f.session.materializedHud().actor("image-2").getWidth(), 0.001f);
            assertEquals(10f, f.session.materializedHud().actor("image-2").getHeight(), 0.001f);

            assertTrue(f.hud.editSession().undo());
            assertEquals("image-1", cell(f.hud.document().root, 0).content.id);
            assertNull(cell(f.hud.document().root, 1).content);
            assertTrue(f.hud.editSession().redo());
            assertEquals("image-1", cell(f.hud.document().root, 0).content.id);
            assertEquals("image-2", cell(f.hud.document().root, 1).content.id);

            f.hud.editSession().edit("Constrain first image", candidate -> {
                var relation = HudLayoutAuthoring.containingCell(candidate, "image-1");
                relation.constraints.prefWidth = 7f;
                relation.constraints.prefHeight = 6f;
                return candidate;
            });
            table = (Table) f.session.materializedHud().actor("table-root");
            table.validate();
            firstActor = (Image) f.session.materializedHud().actor("image-1");
            assertEquals("drawable preference remains natural", 20f, firstActor.getPrefWidth(), 0.001f);
            assertEquals(7f, firstActor.getWidth(), 0.001f);
            assertEquals(6f, firstActor.getHeight(), 0.001f);
        } finally {
            if (f != null) f.close();
            Gdx.graphics = previousGraphics;
        }
    }

    @Test public void windowImageDropUsesCellOnlyInsideItsVisibleContent() throws Exception {
        Graphics previousGraphics = Gdx.graphics;
        Gdx.graphics = logicalGraphics(400, 300);
        ActualFixture f = null;
        try {
            HudNode window = new HudNode("window", HudNodeKind.WINDOW);
            window.window = new games.pixscape.runtime.hud.document.HudWindowData();
            window.table = HudLayoutAuthoring.newTableLayout(window, 1, 1, false);
            window.actor.width = 100f;
            window.actor.height = 100f;
            f = actualFixture(window);
            dropAtHud(f, 40f, 90f);
            assertEquals(0, f.hud.editSession().historySize());
            dropAtHud(f, 40f, 40f);
            assertEquals(1, f.hud.editSession().historySize());
            assertEquals("image-1", cell(f.hud.document().root, 0).content.id);
            assertTrue(f.hud.editSession().undo());
            assertTrue(f.hud.editSession().redo());
        } finally {
            if (f != null) f.close();
            Gdx.graphics = previousGraphics;
        }
    }

    @Test public void imagePayloadDropUsesNormalEditSelectionCoordinatesAndHistory() throws Exception {
        Fixture f = fixture();
        HudFreePlacement placement = new HudFreePlacement();
        placement.offsetX = 123f;
        placement.offsetY = 456f;
        HudEditorSession.ImageDropTarget target =
                new HudEditorSession.ImageDropTarget("root", placement);
        DragPayload payload = imagePayload(f.image.id());

        assertTrue(f.controller.canAccept(payload, target, f.hud, f.hud));
        assertEquals("hover must not author", 0, f.hud.editSession().historySize());
        assertTrue(f.controller.drop(payload, target, f.hud, f.hud));

        assertEquals(1, f.hud.document().root.children.size());
        var child = f.hud.document().root.children.get(0);
        assertEquals(HudNodeKind.IMAGE, child.node.kind);
        assertEquals("test__a1", child.node.image.resourceName);
        assertEquals(HudPlacementKind.FREE, child.placementKind);
        assertEquals(123f, child.free.offsetX, 0f);
        assertEquals(456f, child.free.offsetY, 0f);
        assertEquals("image-1", f.session.selectedNodeId());
        assertEquals("image-1", f.session.document().root.children.get(0).node.id);
        assertTrue(f.hud.isDirty());
        assertEquals(1, f.hud.editSession().historySize());
        assertFalse(f.controller.canAccept(payload,
                new HudEditorSession.ImageDropTarget("image-1", null), f.hud, f.hud));

        assertTrue(f.hud.editSession().undo());
        assertTrue(f.hud.document().root.children.isEmpty());
        assertTrue(f.hud.editSession().redo());
        assertEquals(HudNodeKind.IMAGE, f.hud.document().root.children.get(0).node.kind);
        f.close();
    }

    @Test public void managedTargetsKeepCellDirectAndSingleChildCapacityPolicies() throws Exception {
        Fixture f = fixture();
        f.hud.editSession().edit("Add managed layouts", candidate -> {
            games.pixscape.studio.service.hud.HudLayoutAuthoring.addChild(
                    candidate, "root", HudNodeKind.TABLE);
            games.pixscape.studio.service.hud.HudLayoutAuthoring.addChild(
                    candidate, "root", HudNodeKind.STACK);
            games.pixscape.studio.service.hud.HudLayoutAuthoring.addChild(
                    candidate, "root", HudNodeKind.CONTAINER);
            return candidate;
        });
        HudEditorSession.ImageDropTarget table =
                new HudEditorSession.ImageDropTarget("table-1", null);
        HudEditorSession.ImageDropTarget stack =
                new HudEditorSession.ImageDropTarget("stack-1", null);
        HudEditorSession.ImageDropTarget container =
                new HudEditorSession.ImageDropTarget("container-1", null);

        assertTrue(f.controller.drop(imagePayload(f.image.id()), table, f.hud, f.hud));
        assertTrue(f.controller.drop(imagePayload(f.image.id()), stack, f.hud, f.hud));
        assertTrue(f.controller.drop(imagePayload(f.image.id()), container, f.hud, f.hud));
        assertFalse(f.controller.drop(imagePayload(f.image.id()), container, f.hud, f.hud));
        assertEquals("image-1", cell(f.hud.document().root.children.get(0).node, 0).content.id);
        assertEquals(HudPlacementKind.DIRECT,
                f.hud.document().root.children.get(1).node.children.get(0).placementKind);
        assertEquals(HudPlacementKind.DIRECT,
                f.hud.document().root.children.get(2).node.children.get(0).placementKind);
        assertEquals(1, f.hud.document().root.children.get(2).node.children.size());
        f.close();
    }

    @Test public void cancellingSharedDragMutatesNothing() throws Exception {
        Fixture f = fixture();
        DragContext.get().begin(imagePayload(f.image.id()));
        DragContext.get().cancel();
        assertEquals(0, f.hud.editSession().historySize());
        assertTrue(f.hud.document().root.children.isEmpty());
        f.close();
    }

    @Test public void documentSwitchPermanentlyInvalidatesCurrentDrag() throws Exception {
        Fixture f = fixture();
        DragPayload payload = imagePayload(f.image.id());
        DragContext.get().begin(payload);
        f.controller.observeActiveDocument();
        HudScreenEditorDocument other = f.manager.openHudScreen(new HudScreenEditorDocument(
                "hud/other", "Other", asset("hud/other"),
                new HudDocumentV1(new HudNode("other-root", HudNodeKind.GROUP))));
        f.controller.observeActiveDocument();
        f.manager.activate(f.hud.key());
        f.controller.observeActiveDocument();

        assertFalse(DragContext.get().active());
        assertFalse(f.controller.isShowingForbiddenFeedback());
        assertFalse(f.controller.canAcceptObserved(payload,
                new HudEditorSession.ImageDropTarget("root", new HudFreePlacement()), f.hud));
        assertEquals(0, f.hud.editSession().historySize());
        assertEquals(0, other.editSession().historySize());
        DragContext.get().cancel();
        f.close();
    }

    @Test public void unsupportedInactiveInvalidAndStaleTargetsNeverPublish() throws Exception {
        Fixture f = fixture();
        HudEditorSession.ImageDropTarget root =
                new HudEditorSession.ImageDropTarget("root", new HudFreePlacement());
        DragPayload image = imagePayload(f.image.id());

        DragPayload animation = new DragPayload();
        animation.type = "anim-sheet";
        animation.assetId = f.image.id();
        assertFalse(HudAssetDropController.isImagePayload(animation));
        assertFalse(f.controller.canAccept(animation, root, f.hud, f.hud));
        assertFalse(f.controller.canAccept(image, null, f.hud, f.hud));
        assertFalse(f.controller.canAccept(image, root, null, f.hud));

        HudScreenEditorDocument other = f.manager.openHudScreen(new HudScreenEditorDocument(
                "hud/other", "Other", asset("hud/other"),
                new HudDocumentV1(new HudNode("other-root", HudNodeKind.GROUP))));
        assertFalse(f.controller.drop(image, root, f.hud, other));
        assertEquals(0, f.hud.editSession().historySize());
        assertEquals(0, other.editSession().historySize());
        f.close();
    }

    @Test public void onlyProjectImageAssetIdentityIsEligible() throws Exception {
        Fixture f = fixture();
        AssetMeta animation = f.database.registerIfAbsent(AssetType.ANIMATION,
                "animations/walk", "orig/animations/walk__a2.png", AssetMeta.AssetScope.USER);
        f.root.child(animation.sourceRelPath()).writeString("source", false);
        DragPayload disguised = imagePayload(animation.id());
        HudEditorSession.ImageDropTarget root =
                new HudEditorSession.ImageDropTarget("root", new HudFreePlacement());

        assertFalse(f.controller.canAccept(disguised, root, f.hud, f.hud));
        assertEquals(0, f.hud.editSession().historySize());
        f.close();
    }

    @Test public void imageDropAssignsImageButtonUpStateInOneUndoableOperation() throws Exception {
        Fixture f = fixture();
        f.hud.editSession().edit("Add button", document -> {
            HudNode button = new HudNode("button", HudNodeKind.IMAGE_BUTTON);
            button.imageButton = new games.pixscape.runtime.hud.document.HudImageButtonData();
            document.root.children.add(games.pixscape.runtime.hud.document.HudChild.free(
                    button, new HudFreePlacement()));
            return document;
        });
        int historyBeforeDrop = f.hud.editSession().historySize();
        HudEditorSession.ImageDropTarget target = new HudEditorSession.ImageDropTarget(
                null, null, 0f, 0f, "button");

        assertTrue(f.controller.drop(imagePayload(f.image.id()), target, f.hud, f.hud));
        HudNode button = f.hud.document().root.children.get(0).node;
        assertEquals(historyBeforeDrop + 1, f.hud.editSession().historySize());
        assertEquals("test__a1", button.imageButton.imageUp.resourceName);
        assertTrue(f.hud.editSession().undo());
        assertNull(f.hud.document().root.children.get(0).node.imageButton.imageUp);
        assertTrue(f.hud.editSession().redo());
        assertEquals("test__a1", f.hud.document().root.children.get(0).node.imageButton.imageUp.resourceName);
        f.close();
    }

    @Test public void imageDropAssignsImageTextButtonUpStateInOneUndoableOperation() throws Exception {
        Fixture f = fixture();
        f.hud.editSession().edit("Add button", document -> {
            HudNode button = new HudNode("button", HudNodeKind.IMAGE_TEXT_BUTTON);
            button.imageTextButton = new games.pixscape.runtime.hud.document.HudImageTextButtonData();
            button.imageTextButton.text = "Button";
            document.root.children.add(games.pixscape.runtime.hud.document.HudChild.free(
                    button, new HudFreePlacement()));
            return document;
        });
        int historyBeforeDrop = f.hud.editSession().historySize();
        HudEditorSession.ImageDropTarget target = new HudEditorSession.ImageDropTarget(
                null, null, 0f, 0f, "button");

        assertTrue(f.controller.drop(imagePayload(f.image.id()), target, f.hud, f.hud));
        HudNode button = f.hud.document().root.children.get(0).node;
        assertEquals(historyBeforeDrop + 1, f.hud.editSession().historySize());
        assertEquals("test__a1", button.imageTextButton.imageUp.resourceName);
        assertTrue(f.hud.editSession().undo());
        assertNull(f.hud.document().root.children.get(0).node.imageTextButton.imageUp);
        assertTrue(f.hud.editSession().redo());
        assertEquals("test__a1", f.hud.document().root.children.get(0).node
                .imageTextButton.imageUp.resourceName);
        f.close();
    }

    private Fixture fixture() throws Exception {
        FileHandle root = new FileHandle(temporary.newFolder());
        AssetMetaDatabase database = new AssetMetaDatabase();
        AssetMeta image = database.registerIfAbsent(AssetType.IMAGE, "images/test",
                "orig/images/test__a1.png", AssetMeta.AssetScope.USER);
        root.child(image.sourceRelPath()).writeString("source", false);
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "Main", asset("hud/main"),
                new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP))));
        hud.setSelectedNodeId("root");
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        HudImageAuthoringService authoring = new HudImageAuthoringService(
                () -> root, () -> database, manager, session);
        ArrayList<RuntimeException> failures = new ArrayList<>();
        HudAssetDropController controller = new HudAssetDropController(
                session, authoring, manager, failures::add);
        return new Fixture(root, database, image, manager, hud, session, authoring, controller);
    }

    private static HudScreenAsset asset(String screenId) {
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = screenId + ".json";
        return asset;
    }

    private ActualFixture actualFixture() throws Exception {
        HudNode rootNode = new HudNode("root", HudNodeKind.GROUP);
        rootNode.actor.width = 100f;
        rootNode.actor.height = 100f;
        HudNode existing = new HudNode("existing", HudNodeKind.GROUP);
        existing.actor.width = 10f;
        existing.actor.height = 10f;
        rootNode.children.add(games.pixscape.runtime.hud.document.HudChild.free(
                existing, new HudFreePlacement()));
        return actualFixture(rootNode);
    }

    private ActualFixture actualTableFixture() throws Exception {
        HudNode table = new HudNode("table-root", HudNodeKind.TABLE);
        table.table = HudLayoutAuthoring.newTableLayout(table, 1, 2, false);
        table.actor.width = 100f;
        table.actor.height = 100f;
        return actualFixture(table);
    }

    private static games.pixscape.runtime.hud.document.HudTableCell cell(HudNode table, int index) {
        return table.table.rows.get(0).cells.get(index);
    }

    private ActualFixture actualFixture(HudNode rootNode) throws Exception {
        FileHandle root = new FileHandle(temporary.newFolder());
        AssetMetaDatabase database = new AssetMetaDatabase();
        AssetMeta image = database.registerIfAbsent(AssetType.IMAGE, "images/test",
                "orig/images/test__a1.png", AssetMeta.AssetScope.USER);
        Pixmap pixels = new Pixmap(20, 10, Pixmap.Format.RGBA8888);
        try {
            pixels.setColor(1f, 1f, 1f, 1f);
            pixels.fill();
            PixmapIO.writePNG(root.child(image.sourceRelPath()), pixels);
        } finally {
            pixels.dispose();
        }
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/main.json";
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "Main", asset, new HudDocumentV1(rootNode)));
        hud.setSelectedNodeId(rootNode.id);
        HudEditorSession session = new HudEditorSession(() -> database,
                HudAssetDropControllerTest::inertBatch);
        session.open(root, hud);
        assertEquals(session.errorMessage(), HudEditorSession.Status.READY, session.status());
        var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
        configure.setAccessible(true);
        assertTrue((Boolean) configure.invoke(session, new Rectangle(40f, 30f, 100f, 100f)));
        assertEquals(40, session.hudViewport().getScreenX());
        HudImageAuthoringService authoring = new HudImageAuthoringService(
                () -> root, () -> database, manager, session);
        ArrayList<RuntimeException> failures = new ArrayList<>();
        HudAssetDropController controller = new HudAssetDropController(
                session, authoring, manager, failures::add);
        return new ActualFixture(image, hud, session, authoring, controller, failures);
    }

    private static void dropAtHud(ActualFixture fixture, float hudX, float hudY) {
        DragPayload payload = imagePayload(fixture.image.id());
        payload.imageWidth = 20;
        payload.imageHeight = 10;
        DragContext.get().begin(payload);
        fixture.controller.observeActiveDocument();
        Vector2 pointer = fixture.session.hudViewport().project(new Vector2(hudX, hudY));
        DragContext.get().signalRelease();
        fixture.controller.updateAt(Math.round(pointer.x), 300 - Math.round(pointer.y), 300, true);
        assertFalse(DragContext.get().active());
        assertTrue(fixture.failures.isEmpty());
    }

    private static DragPayload imagePayload(int assetId) {
        DragPayload payload = new DragPayload();
        payload.type = "image-file";
        payload.assetId = assetId;
        return payload;
    }

    private record Fixture(FileHandle root, AssetMetaDatabase database, AssetMeta image,
                           EditorDocumentManager manager, HudScreenEditorDocument hud,
                           HudEditorSession session, HudImageAuthoringService authoring,
                           HudAssetDropController controller) {
        void close() { controller.close(); authoring.close(); }
    }

    private record ActualFixture(AssetMeta image, HudScreenEditorDocument hud,
                                 HudEditorSession session, HudImageAuthoringService authoring,
                                 HudAssetDropController controller,
                                 ArrayList<RuntimeException> failures) {
        void close() { controller.close(); authoring.close(); session.dispose(); }
    }

    private static Batch inertBatch() {
        return (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(),
                new Class<?>[]{Batch.class},
                (proxy, method, args) -> primitiveDefault(method.getReturnType()));
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
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
}
