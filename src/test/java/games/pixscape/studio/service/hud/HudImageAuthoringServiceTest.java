package games.pixscape.studio.service.hud;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.ui.asset.AssetNode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/** Image placement remains an immediate authored edit; Runtime work is no longer its concern. */
public class HudImageAuthoringServiceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void addPublishesImageImmediatelyWithoutOwningRuntimePreparation() throws Exception {
        FileHandle root = new FileHandle(temporary.newFolder());
        AssetMetaDatabase database = new AssetMetaDatabase();
        AssetMeta meta = database.registerIfAbsent(AssetType.IMAGE, "images/first",
                "orig/images/first__a1.png", AssetMeta.AssetScope.USER);
        root.child(meta.sourceRelPath()).writeString("source", false);
        AssetNode image = AssetNode.fromAssetMeta(AssetNode.Kind.IMAGE, AssetNode.Root.IMAGES, "first", meta);
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument("hud/main", "Main",
                asset("hud/main"), new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP))));
        hud.setSelectedNodeId("root");
        HudEditorSession preview = projectedPreview(hud);
        HudDocumentEditSession.ActivePreview binding = (asset, candidate, validation) -> {
            try { set(preview, "document", candidate); }
            catch (Exception failure) { throw new AssertionError(failure); }
        };
        hud.editSession().bindActivePreview(binding);

        try (HudImageAuthoringService service = new HudImageAuthoringService(
                () -> root, () -> database, manager, preview)) {
            assertEquals("image-1", service.add(image));
            assertEquals(1, hud.editSession().historySize());
            assertEquals("image-1", hud.document().root.children.get(0).node.id);
            assertNull("Runtime binding is not updated by immediate authoring", hud.asset().atlasId);
            assertTrue(hud.isDirty());
        }
    }

    @Test public void invalidSelectionDoesNotPublishAnImage() throws Exception {
        FileHandle root = new FileHandle(temporary.newFolder());
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument("hud/main", "Main",
                asset("hud/main"), new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP))));
        HudEditorSession preview = projectedPreview(hud);
        try (HudImageAuthoringService service = new HudImageAuthoringService(
                () -> root, AssetMetaDatabase::new, manager, preview)) {
            assertNull(service.add(null));
            assertEquals(0, hud.editSession().historySize());
        }
    }

    private static HudEditorSession projectedPreview(HudScreenEditorDocument hud) throws Exception {
        HudEditorSession preview = new HudEditorSession();
        set(preview, "editorDocument", hud);
        set(preview, "screenId", hud.screenId());
        set(preview, "asset", hud.asset());
        set(preview, "document", hud.document());
        set(preview, "validation", new HudDocumentValidator().validate(hud.document()));
        set(preview, "status", HudEditorSession.Status.READY);
        set(preview, "selectedNodeId", "root");
        return preview;
    }

    private static HudScreenAsset asset(String screenId) {
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = screenId + ".json";
        return asset;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
