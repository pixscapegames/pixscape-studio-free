package games.pixscape.studio.ui.main;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.SceneEditorDocument;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.service.hud.HudDocumentPersistenceService;
import games.pixscape.studio.service.hud.HudScreenAssetAuthoringService;
import games.pixscape.studio.service.hud.SceneHudAssociationService;
import games.pixscape.studio.ui.asset.dnd.DragContext;
import games.pixscape.studio.ui.asset.dnd.DragPayload;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SceneHudAssetDropControllerTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @After public void clearDrag() { DragContext.get().cancel(); }

    @Test public void realHudPayloadAssignsCapturedSceneAndDocumentSwitchInvalidatesDrag()
            throws Exception {
        FileHandle root = new FileHandle(temporary.newFolder());
        new HudScreenAssetAuthoringService().create(root, "status", 320, 180);
        ProjectConfig cfg = new ProjectConfig();
        cfg.createSceneMeta("A");
        cfg.createSceneMeta("B");
        SceneEditorContext contextA = new SceneEditorContext("scene1", new StudioEditingModeService());
        SceneEditorContext contextB = new SceneEditorContext("scene2", new StudioEditingModeService());
        EditorDocumentManager documents = new EditorDocumentManager();
        SceneEditorDocument sceneA = documents.openScene("scene1", "A", contextA);
        SceneEditorDocument sceneB = documents.openScene("scene2", "B", contextB);
        SceneHudAssociationService associations = new SceneHudAssociationService(
                () -> root, () -> cfg, new HudDocumentPersistenceService(),
                ignored -> {}, (sceneTag, screenId) -> {});
        SceneHudAssetDropController controller = new SceneHudAssetDropController(
                associations, documents);

        documents.activate(sceneA.key());
        DragPayload payload = hudPayload("hud/status");
        DragContext.get().begin(payload);
        controller.observeActiveDocument();
        assertTrue(controller.canAccept(payload, contextA));
        assertTrue(controller.drop(payload, contextA));
        assertEquals("hud/status", cfg.getSceneMeta("A").defaultHudScreenId);
        DragContext.get().cancel();

        DragPayload switched = hudPayload("hud/status");
        DragContext.get().begin(switched);
        controller.observeActiveDocument();
        documents.activate(sceneB.key());
        controller.observeActiveDocument();

        assertFalse(DragContext.get().active());
        assertFalse(controller.canAccept(switched, contextB));
        assertNull(cfg.getSceneMeta("B").defaultHudScreenId);
    }

    private static DragPayload hudPayload(String screenId) {
        DragPayload payload = new DragPayload();
        payload.type = "hud-screen";
        payload.path = screenId;
        return payload;
    }
}
