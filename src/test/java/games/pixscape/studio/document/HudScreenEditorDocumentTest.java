package games.pixscape.studio.document;

import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import org.junit.Test;

import static org.junit.Assert.*;

public class HudScreenEditorDocumentTest {
    @Test
    public void existingAssetWithoutDocumentIdIsRejectedWithoutMutation() {
        HudScreenAsset asset = new HudScreenAsset();

        try {
            new HudScreenEditorDocument("hud/main", "Main", asset,
                    new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP)));
            fail("Expected a document ID validation failure.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("documentId is required"));
        }

        assertNull(asset.documentId);
    }

    @Test
    public void selectedNodeIdSurvivesRebuildWhilePresentAndClearsWhenRemoved() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        root.children.add(HudChild.direct(new HudNode("child", HudNodeKind.GROUP)));
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/main.json";
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "Main", asset, new HudDocumentV1(root));
        document.setSelectedNodeId("child");

        document.editSession().edit("Resize root", candidate -> {
            candidate.root.actor.width = 10f;
            return candidate;
        });
        assertEquals("child", document.selectedNodeId());

        document.editSession().edit("Remove child", candidate -> {
            candidate.root.children.clear();
            return candidate;
        });
        assertNull(document.selectedNodeId());
    }

    @Test
    public void rootHistoryAndSelectionRemainIsolatedPerHudDocument() {
        HudScreenAsset firstAsset = new HudScreenAsset();
        firstAsset.documentId = "hud/first.json";
        HudScreenAsset secondAsset = new HudScreenAsset();
        secondAsset.documentId = "hud/second.json";
        HudScreenEditorDocument first = new HudScreenEditorDocument(
                "hud/first", "First", firstAsset, new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP)));
        HudScreenEditorDocument second = new HudScreenEditorDocument(
                "hud/second", "Second", secondAsset, new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP)));

        first.editSession().edit("Resize root", candidate -> {
            candidate.root.actor.width = 10f;
            return candidate;
        });
        first.setSelectedNodeId("root");

        assertEquals("root", first.document().root.id);
        assertEquals("root", first.selectedNodeId());
        assertTrue(first.isDirty());
        assertEquals("root", second.document().root.id);
        assertNull(second.selectedNodeId());
        assertFalse(second.isDirty());
        assertEquals(0, second.editSession().historySize());
    }
}
