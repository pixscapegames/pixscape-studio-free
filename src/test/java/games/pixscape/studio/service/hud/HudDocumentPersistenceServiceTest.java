package games.pixscape.studio.service.hud;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.HudScreenAssetLoader;
import games.pixscape.runtime.hud.HudTextureProfile;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudPlacementKind;
import games.pixscape.studio.document.HudScreenEditorDocument;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class HudDocumentPersistenceServiceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @BeforeClass public static void bootGdx() {
        if (Gdx.files == null) new HeadlessApplication(new ApplicationAdapter() {},
                new HeadlessApplicationConfiguration());
    }

    @Test
    public void cleanLoadAndDirtySaveRoundTripDeterministicCodecState() throws Exception {
        var root = Gdx.files.absolute(temporary.newFolder().getAbsolutePath());
        root.child("hud/main.hudscreen").writeString("""
                {"schemaVersion":1,
                 "documentId":"hud/main.json","textureProfileId":"default"}
                """, false, "UTF-8");
        HudDocumentCodec codec = new HudDocumentCodec();
        root.child("hud/main.json").writeString(codec.write(document(10f)), false, "UTF-8");
        HudDocumentPersistenceService persistence = new HudDocumentPersistenceService();
        var loaded = persistence.load(root, "hud/main");
        HudDocumentEditSession session = new HudDocumentEditSession(loaded.asset(), loaded.document());
        assertFalse(session.isDirty());
        session.edit("Resize", candidate -> {
            candidate.root.actor.width = 25f;
            return candidate;
        });
        persistence.save(root, session);
        assertFalse(session.isDirty());
        assertEquals(codec.write(session.document()), root.child("hud/main.json").readString("UTF-8"));
        assertEquals(25f, persistence.load(root, "main").document().root.actor.width, 0f);
    }

    @Test
    public void assignedSkinPersistsAndReopensThroughExistingScreenMetadata() throws Exception {
        var root = Gdx.files.absolute(temporary.newFolder().getAbsolutePath());
        root.child("hud/main.hudscreen").writeString("""
                {"schemaVersion":1,
                 "documentId":"hud/main.json","textureProfileId":"default"}
                """, false, "UTF-8");
        root.child("hud/main.json").writeString(
                new HudDocumentCodec().write(document(10f)), false, "UTF-8");
        HudDocumentPersistenceService persistence = new HudDocumentPersistenceService();
        var loaded = persistence.load(root, "main");
        HudScreenEditorDocument editor = new HudScreenEditorDocument(
                "hud/main", "Main", loaded.asset(), loaded.document());

        editor.editSession().editSkin(
                "Assign HUD Screen Skin", "orig/skins/17/game.json");
        persistence.save(root, editor);

        assertFalse(editor.isDirty());
        assertEquals("orig/skins/17/game.json",
                persistence.load(root, "main").asset().skinId);
    }

    @Test
    public void adaptiveRootTableAndCellLimitsSurviveSaveAndReopen() throws Exception {
        var root = Gdx.files.absolute(temporary.newFolder().getAbsolutePath());
        root.child("hud/adaptive.hudscreen").writeString("""
                {"schemaVersion":1,
                 "documentId":"hud/adaptive.json","textureProfileId":"default"}
                """, false, "UTF-8");
        root.child("hud/adaptive.json").writeString(
                new HudDocumentCodec().write(document(0f)), false, "UTF-8");
        HudDocumentPersistenceService persistence = new HudDocumentPersistenceService();
        var loaded = persistence.load(root, "adaptive");
        HudDocumentEditSession session = new HudDocumentEditSession(loaded.asset(), loaded.document());
        session.edit("Add adaptive table", candidate -> {
            String id = HudLayoutAuthoring.addChild(candidate, "root", HudNodeKind.TABLE);
            var table = HudLayoutAuthoring.node(candidate, id);
            table.table.rows.get(0).cells.get(0).constraints.maxWidth = 280f;
            table.table.rows.get(0).cells.get(0).constraints.expandX = true;
            return candidate;
        });
        persistence.save(root, session);

        var reopened = persistence.load(root, "adaptive").document();
        var relation = reopened.root.children.get(0);
        assertEquals(HudPlacementKind.DIRECT, relation.placementKind);
        assertTrue(relation.node.fillParent);
        assertEquals(Float.valueOf(280f),
                relation.node.table.rows.get(0).cells.get(0).constraints.maxWidth);
        assertTrue(relation.node.table.rows.get(0).cells.get(0).constraints.expandX);
    }

    @Test
    public void missingDocumentReferenceIsRejectedDuringLoad() throws Exception {
        var root = Gdx.files.absolute(temporary.newFolder().getAbsolutePath());
        root.child("hud/empty.hudscreen").writeString("""
                {"schemaVersion":1,
                 "textureProfileId":"default"}
                """, false, "UTF-8");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new HudDocumentPersistenceService().load(root, "empty"));
        assertTrue(failure.getMessage().contains("documentId is required"));
        assertFalse(root.child("hud/empty.json").exists());
    }

    @Test
    public void failedAtomicWriteLeavesDirtyStateAndHistoryUntouched() throws Exception {
        var root = Gdx.files.absolute(temporary.newFolder().getAbsolutePath());
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/main.json";
        HudDocumentEditSession session = new HudDocumentEditSession(asset, document(1f));
        session.edit("Resize", candidate -> {
            candidate.root.actor.width = 2f;
            return candidate;
        });
        HudDocumentPersistenceService persistence = new HudDocumentPersistenceService(
                new HudScreenAssetLoader(), new HudDocumentCodec(),
                (target, content) -> { throw new IllegalStateException("disk full"); });
        try {
            persistence.save(root, session);
            fail("Expected write failure");
        } catch (IllegalStateException expected) {
            assertEquals("disk full", expected.getMessage());
        }
        assertTrue(session.isDirty());
        assertEquals(1, session.historySize());
        assertTrue(session.canUndo());
    }

    @Test
    public void emptyAndOccupiedContainerRoundTripWithoutSchemaChange() throws Exception {
        var root = Gdx.files.absolute(temporary.newFolder().getAbsolutePath());
        root.child("hud/layout.hudscreen").writeString("""
                {"schemaVersion":1,
                 "documentId":"hud/layout.json","textureProfileId":"default"}
                """, false, "UTF-8");
        root.child("hud/layout.json").writeString(new HudDocumentCodec().write(document(0f)), false, "UTF-8");
        HudDocumentPersistenceService persistence = new HudDocumentPersistenceService();
        var loaded = persistence.load(root, "layout");
        HudScreenEditorDocument editor = new HudScreenEditorDocument(
                "hud/layout", "Layout", loaded.asset(), loaded.document());
        editor.editSession().edit("Add empty Container", candidate -> {
            HudLayoutAuthoring.addChild(candidate, "root", HudNodeKind.CONTAINER);
            return candidate;
        });
        persistence.save(root, editor);
        editor.close();

        var emptyState = persistence.load(root, "layout");
        assertEquals(HudDocumentV1.CURRENT_SCHEMA_VERSION, emptyState.document().schemaVersion);
        assertEquals(1, emptyState.document().root.children.size());
        assertEquals(HudNodeKind.CONTAINER, emptyState.document().root.children.get(0).node.kind);
        assertEquals(HudPlacementKind.FREE,
                emptyState.document().root.children.get(0).placementKind);
        assertTrue(emptyState.document().root.children.get(0).node.children.isEmpty());
        HudScreenEditorDocument occupied = new HudScreenEditorDocument(
                "hud/layout", "Layout", emptyState.asset(), emptyState.document());
        assertFalse(occupied.isDirty());
        occupied.editSession().edit("Add Table to Container", candidate -> {
            HudLayoutAuthoring.addChild(candidate, "container-1", HudNodeKind.TABLE);
            return candidate;
        });
        persistence.save(root, occupied);
        occupied.close();

        var occupiedState = persistence.load(root, "layout");
        HudScreenEditorDocument reopened = new HudScreenEditorDocument(
                "hud/layout", "Layout", occupiedState.asset(), occupiedState.document());
        assertFalse(reopened.isDirty());
        var container = reopened.document().root.children.get(0).node;
        assertNotNull(container.container);
        assertEquals(1, container.children.size());
        assertEquals("table-1", container.children.get(0).node.id);
        assertEquals(HudPlacementKind.DIRECT, container.children.get(0).placementKind);
        reopened.close();
    }

    @Test
    public void existingDocumentSavePersistsAuthoredDocumentWithoutChangingResourceBindings() throws Exception {
        var root = Gdx.files.absolute(temporary.newFolder().getAbsolutePath());
        root.child("hud/shared.hudscreen").writeString("""
                {"schemaVersion":1,
                 "documentId":"hud/shared.json","textureProfileId":"hud-fixed-2048-linear-clamp"}
                """, false, "UTF-8");
        HudDocumentCodec codec = new HudDocumentCodec();
        root.child("hud/shared.json").writeString(codec.write(document(0f)), false, "UTF-8");
        HudDocumentPersistenceService persistence = new HudDocumentPersistenceService();
        var loaded = persistence.load(root, "shared");
        HudScreenEditorDocument editor = new HudScreenEditorDocument(
                "hud/shared", "Shared", loaded.asset(), loaded.document());
        editor.editSession().edit("Add Image", candidate -> {
            HudLayoutAuthoring.addImage(candidate, "root", "image-1", "portrait__a7");
            return candidate;
        });

        persistence.save(root, editor);
        var reopened = persistence.load(root, "shared");
        assertEquals(HudScreenAsset.CURRENT_SCHEMA_VERSION, reopened.asset().schemaVersion);
        assertNull(reopened.asset().atlasId);
        assertEquals(HudTextureProfile.DEFAULT_ID, reopened.asset().textureProfileId);
        assertNull(reopened.asset().skinId);
        assertEquals("portrait__a7",
                reopened.document().root.children.get(0).node.image.resourceName);
    }


    private static HudDocumentV1 document(float width) {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        root.actor.width = width;
        return new HudDocumentV1(root);
    }

    @Test public void existingPairRollsBackEitherFailedReplacementAndRetrySucceeds() throws Exception {
        for (int failAt : new int[]{1, 2}) {
            var root = Gdx.files.absolute(temporary.newFolder().getAbsolutePath());
            root.child("hud/main.hudscreen").writeString("""
                    {"schemaVersion":1,
                     "documentId":"hud/main.json","textureProfileId":"default"}
                    """, false, "UTF-8");
            root.child("hud/main.json").writeString(new HudDocumentCodec().write(document(1f)), false, "UTF-8");
            byte[] oldAsset = root.child("hud/main.hudscreen").readBytes();
            byte[] oldDocument = root.child("hud/main.json").readBytes();
            var loaded = new HudDocumentPersistenceService().load(root, "main");
            var editor = new HudScreenEditorDocument("hud/main", "Main", loaded.asset(), loaded.document());
            editor.editSession().edit("Resize", candidate -> { candidate.root.actor.width = 99f; return candidate; });
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var failing = new HudDocumentPersistenceService(new HudScreenAssetLoader(), new HudDocumentCodec(),
                    (target, content) -> {
                        games.pixscape.studio.io.StudioIO.writeUtf8Atomic(target, content);
                        if (calls.incrementAndGet() == failAt) throw new IllegalStateException("injected publication failure");
                    });
            try { failing.save(root, editor); fail("Expected injected failure"); }
            catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("injected")); }
            assertArrayEquals(oldDocument, root.child("hud/main.json").readBytes());
            assertArrayEquals(oldAsset, root.child("hud/main.hudscreen").readBytes());
            assertFalse(root.child("hud/main.json.bak").exists());
            assertFalse(root.child("hud/main.hudscreen.bak").exists());
            assertTrue(editor.isDirty());
            assertEquals(1, editor.editSession().historySize());
            assertTrue(editor.editSession().canUndo());
            assertFalse(editor.editSession().canRedo());
            assertEquals(99f, editor.document().root.actor.width, 0f);
            new HudDocumentPersistenceService().save(root, editor);
            assertFalse(editor.isDirty());
            var reopened = new HudDocumentPersistenceService().load(root, "main");
            assertEquals(99f, reopened.document().root.actor.width, 0f);
            assertNull(reopened.asset().atlasId);
        }
    }

}
