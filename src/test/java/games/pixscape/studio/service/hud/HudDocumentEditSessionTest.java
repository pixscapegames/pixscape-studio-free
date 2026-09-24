package games.pixscape.studio.service.hud;

import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudLabelData;
import games.pixscape.runtime.hud.document.HudResourceCatalog;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class HudDocumentEditSessionTest {
    @Test
    public void cleanLoadAcceptsAtomicEditAndKeepsCallerAwayFromAuthoritativeDto() {
        HudDocumentEditSession session = session(document("root"));
        assertFalse(session.isDirty());
        assertEquals(0, session.historySize());
        HudDocumentV1 leaked = session.document();
        leaked.root.id = "outside";
        assertEquals("root", session.document().root.id);

        resize(session, 320f);

        assertEquals(320f, session.document().root.actor.width, 0f);
        assertEquals(1, session.historySize());
        assertTrue(session.isDirty());
    }

    @Test
    public void invalidCandidateRetainsModelHistoryDirtyAndPreview() {
        HudDocumentEditSession session = session(document("root"));
        AtomicInteger previewInstalls = new AtomicInteger();
        session.bindActivePreview((asset, candidate, validation) -> previewInstalls.incrementAndGet());
        try {
            session.edit("Duplicate root", candidate -> {
                candidate.root.children.add(HudChild.direct(new HudNode("root", HudNodeKind.GROUP)));
                return candidate;
            });
            fail("Expected invalid candidate rejection");
        } catch (HudEditRejectedException expected) {
            assertTrue(expected.getMessage().contains("DUPLICATE_NODE_ID"));
        }
        assertEquals("root", session.document().root.id);
        assertEquals(0, session.historySize());
        assertFalse(session.isDirty());
        assertEquals(0, previewInstalls.get());
    }

    @Test
    public void previewFailureRejectsWithoutPublishingOrAdvancingHistory() {
        HudDocumentEditSession session = session(document("root"));
        session.bindActivePreview((asset, candidate, validation) -> {
            throw new IllegalStateException("materialization failed");
        });
        try {
            resize(session, 100f);
            fail("Expected preview rejection");
        } catch (HudEditRejectedException expected) {
            assertTrue(expected.getMessage().contains("materialization failed"));
        }
        assertEquals(0f, session.document().root.actor.width, 0f);
        assertEquals(0, session.historySize());
        assertFalse(session.isDirty());
    }

    @Test
    public void undoRedoBranchingAndSavedRevisionHaveExactDirtySemantics() {
        HudDocumentEditSession session = session(document("root"));
        resize(session, 10f);
        assertTrue(session.isDirty());
        assertTrue(session.undo());
        assertFalse(session.isDirty());
        assertTrue(session.redo());
        assertTrue(session.isDirty());
        resize(session, 20f);
        session.markSaved();
        assertFalse(session.isDirty());
        assertTrue(session.undo());
        assertTrue(session.isDirty());
        assertTrue(session.redo());
        assertFalse(session.isDirty());
        assertTrue(session.undo());
        resize(session, 30f);
        assertFalse(session.canRedo());
        assertEquals(30f, session.document().root.actor.width, 0f);
        assertTrue(session.isDirty());
    }

    @Test
    public void skinChangeIsOneUndoableMetadataTransaction() {
        HudDocumentV1 document = document("root");
        HudNode label = new HudNode("label", HudNodeKind.LABEL);
        label.label = new HudLabelData();
        label.label.text = "Label";
        label.label.fontAssetId = 42;
        HudNode image = new HudNode("image", HudNodeKind.IMAGE);
        image.image = new games.pixscape.runtime.hud.document.HudImageData();
        image.image.source = games.pixscape.runtime.hud.document.HudImageSource.REGION;
        image.image.resourceName = "icon__a7";
        document.root.children.add(HudChild.direct(label));
        document.root.children.add(HudChild.direct(image));
        String authoredBefore = new games.pixscape.runtime.hud.document.HudDocumentCodec()
                .write(document);
        HudDocumentEditSession session = session(document);
        java.util.List<String> installed = new java.util.ArrayList<>();
        AtomicInteger publications = new AtomicInteger();
        session.bindActivePreview((asset, candidate, validation) -> installed.add(asset.skinId));
        session.addAuthoredPublicationListener(ignored -> publications.incrementAndGet());

        session.editSkin("Assign HUD Screen Skin", "orig/skins/7/game.json");

        assertEquals("orig/skins/7/game.json", session.asset().skinId);
        assertEquals("root", session.document().root.id);
        assertEquals(authoredBefore, new games.pixscape.runtime.hud.document.HudDocumentCodec()
                .write(session.document()));
        assertEquals(1, session.historySize());
        assertEquals(1, publications.get());
        assertTrue(session.isDirty());
        assertEquals(java.util.Arrays.asList("orig/skins/7/game.json"), installed);

        assertTrue(session.undo());
        assertNull(session.asset().skinId);
        assertFalse(session.isDirty());
        assertTrue(session.redo());
        assertEquals("orig/skins/7/game.json", session.asset().skinId);
        assertEquals(java.util.Arrays.asList("orig/skins/7/game.json", null,
                "orig/skins/7/game.json"), installed);
        assertEquals(3, publications.get());
    }

    @Test
    public void rejectedSkinPreviewKeepsMetadataHistoryAndDirtyState() {
        HudDocumentEditSession session = session(document("root"));
        session.bindActivePreview((asset, candidate, validation) -> {
            throw new HudEditRejectedException("Label style 'missing' is unavailable.");
        });

        HudEditRejectedException failure = assertThrows(HudEditRejectedException.class,
                () -> session.editSkin("Assign HUD Screen Skin", "orig/skins/8/bad.json"));

        assertTrue(failure.getMessage().contains("missing"));
        assertNull(session.asset().skinId);
        assertEquals(0, session.historySize());
        assertFalse(session.isDirty());
    }

    @Test
    public void documentChangeListenersObserveEditUndoAndRedoAfterEachPublication() {
        HudDocumentEditSession session = session(document("root"));
        AtomicInteger notifications = new AtomicInteger();
        session.addListener(notifications::incrementAndGet);

        resize(session, 10f);
        assertEquals(1, notifications.get());
        assertTrue(session.undo());
        assertEquals(2, notifications.get());
        assertTrue(session.redo());
        assertEquals(3, notifications.get());
    }

    @Test
    public void authoredPublicationListenersObserveOnlyEditUndoAndRedoAfterTheNewStateIsCurrent() {
        HudDocumentEditSession session = session(document("root"));
        AtomicInteger notifications = new AtomicInteger();
        session.addAuthoredPublicationListener(published -> {
            notifications.incrementAndGet();
            assertSame(session, published);
            assertEquals(notifications.get() % 2 == 1 ? 10f : 0f,
                    published.document().root.actor.width, 0f);
        });

        resize(session, 10f);
        session.markSaved();
        assertEquals(1, notifications.get());
        assertTrue(session.undo());
        assertEquals(2, notifications.get());
        assertTrue(session.redo());
        assertEquals(3, notifications.get());
    }

    @Test
    public void authoredPublicationListenerFailureCannotRollBackPublishedHistory() {
        HudDocumentEditSession session = session(document("root"));
        session.addAuthoredPublicationListener(ignored -> { throw new IllegalStateException("observer failed"); });
        resize(session, 10f);
        assertEquals(1, session.historySize());
        assertEquals(10f, session.document().root.actor.width, 0f);
        assertTrue(session.isDirty());
    }

    @Test
    public void multipleSessionsKeepIndependentHistoryAndPreviewBindings() {
        HudDocumentEditSession first = session(document("first"));
        HudDocumentEditSession second = session(document("second"));
        AtomicInteger firstPreview = new AtomicInteger();
        first.bindActivePreview((asset, candidate, validation) -> firstPreview.incrementAndGet());
        resize(first, 11f);
        resize(second, 22f);
        assertEquals(1, firstPreview.get());
        assertTrue(first.undo());
        assertEquals(2, firstPreview.get());
        assertEquals(22f, second.document().root.actor.width, 0f);
        assertTrue(second.isDirty());
        assertFalse(first.isDirty());
    }

    @Test
    public void explicitResourceCatalogRejectsUnavailableRuntimeResourceBeforePreview() {
        HudDocumentEditSession session = session(document("root"));
        HudResourceCatalog none = new HudResourceCatalog() {
            @Override public boolean hasRegion(String name) { return false; }
            @Override public boolean hasDrawable(String name) { return false; }
            @Override public boolean hasLabelStyle(String name) { return false; }
            @Override public boolean hasTextButtonStyle(String name) { return false; }
        };
        try {
            session.edit("Add label", candidate -> {
                HudNode label = new HudNode("label", HudNodeKind.LABEL);
                label.label = new HudLabelData();
                label.label.text = "Status";
                label.label.styleName = "missing-style";
                candidate.root.children.add(HudChild.direct(label));
                return candidate;
            }, none);
            fail("Expected missing resource rejection");
        } catch (HudEditRejectedException expected) {
            assertTrue(expected.getMessage().contains("missing-style"));
        }
        assertTrue(session.document().root.children.isEmpty());
        assertFalse(session.isDirty());
        assertEquals(0, session.historySize());
    }

    private static HudDocumentEditSession session(HudDocumentV1 document) {
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/main.json";
        return new HudDocumentEditSession(asset, document);
    }

    private static HudDocumentV1 document(String id) {
        return new HudDocumentV1(new HudNode(id, HudNodeKind.GROUP));
    }

    private static void resize(HudDocumentEditSession session, float width) {
        session.edit("Resize", candidate -> {
            candidate.root.actor.width = width;
            return candidate;
        });
    }
}
