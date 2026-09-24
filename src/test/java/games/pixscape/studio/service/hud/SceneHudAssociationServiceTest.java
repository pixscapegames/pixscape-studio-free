package games.pixscape.studio.service.hud;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.document.SceneEditorDocument;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.StudioEditingModeService;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SceneHudAssociationServiceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private ProjectConfig previous;

    @After public void restoreConfiguration() {
        if (previous != null) ProjectConfig.setInstance(previous);
    }

    @Test public void associationReplacementRemovalAndHistoryStayBoundToOwningScene()
            throws Exception {
        Fixture f = fixture();
        f.configuration.setCurrentSceneByName("Scene B");

        assertTrue(f.service.associate(f.sceneA, "hud\\a"));
        assertEquals("hud/a", f.metaA().defaultHudScreenId);
        assertTrue(f.sceneA.isDirty());
        assertEquals(List.of("scene1"), f.invalidations);

        f.sceneA.context().markSaved();
        int cursor = f.sceneA.context().historyManager().getCursor();
        assertFalse(f.service.associate(f.sceneA, "hud/a"));
        assertEquals(cursor, f.sceneA.context().historyManager().getCursor());
        assertFalse(f.sceneA.isDirty());
        assertEquals(1, f.invalidations.size());

        assertTrue(f.service.associate(f.sceneA, "hud/b"));
        assertEquals("hud/b", f.metaA().defaultHudScreenId);
        f.sceneA.context().historyManager().undo();
        assertEquals("hud/a", f.metaA().defaultHudScreenId);
        f.sceneA.context().historyManager().redo();
        assertEquals("hud/b", f.metaA().defaultHudScreenId);

        assertTrue(f.service.remove(f.sceneA));
        assertNull(f.metaA().defaultHudScreenId);
        f.sceneA.context().historyManager().undo();
        assertEquals("hud/b", f.metaA().defaultHudScreenId);
        f.sceneA.context().historyManager().redo();
        assertNull(f.metaA().defaultHudScreenId);
        assertNull(f.metaB().defaultHudScreenId);
        assertEquals(7, f.invalidations.size());
        assertTrue(f.invalidations.stream().allMatch("scene1"::equals));
        assertEquals(7, f.notifications.size());
        assertEquals("scene1=null", f.notifications.get(f.notifications.size() - 1));
    }

    @Test public void invalidReferenceIsRejectedAndPersistedAssociationReloads() throws Exception {
        Fixture f = fixture();

        assertFalse(f.service.associate(f.sceneA, "hud/missing"));
        assertNull(f.metaA().defaultHudScreenId);
        assertEquals(0, f.sceneA.context().historyManager().getCursor());
        assertTrue(f.service.associate(f.sceneA, "hud/a"));

        FileHandle projectFile = f.root.child("project.json");
        ProjectConfig.ProjectIO.saveProject(f.configuration, projectFile);
        ProjectConfig reloaded = ProjectConfig.ProjectIO.loadProject(projectFile);
        assertEquals("hud/a", reloaded.getSceneMeta("Scene A").defaultHudScreenId);
    }

    private Fixture fixture() throws Exception {
        previous = ProjectConfig.getInstance();
        ProjectConfig configuration = new ProjectConfig();
        configuration.projectTitle = "HUD association";
        configuration.projectFileName = "project";
        configuration.exportRootPathDir = "build/export";
        configuration.createSceneMeta("Scene A");
        configuration.createSceneMeta("Scene B");
        ProjectConfig.setInstance(configuration);

        FileHandle root = new FileHandle(temporary.newFolder());
        HudScreenAssetAuthoringService authoring = new HudScreenAssetAuthoringService();
        authoring.create(root, "a", 320, 180);
        authoring.create(root, "b", 640, 360);

        SceneEditorContext contextA = new SceneEditorContext(
                "scene1", new StudioEditingModeService());
        SceneEditorContext contextB = new SceneEditorContext(
                "scene2", new StudioEditingModeService());
        SceneEditorDocument sceneA = new SceneEditorDocument("scene1", "Scene A", contextA);
        SceneEditorDocument sceneB = new SceneEditorDocument("scene2", "Scene B", contextB);
        ArrayList<String> invalidations = new ArrayList<>();
        ArrayList<String> notifications = new ArrayList<>();
        SceneHudAssociationService service = new SceneHudAssociationService(
                () -> root, () -> configuration, new HudDocumentPersistenceService(),
                invalidations::add,
                (sceneTag, screenId) -> notifications.add(sceneTag + "=" + screenId));
        return new Fixture(root, configuration, sceneA, sceneB, service,
                invalidations, notifications);
    }

    private record Fixture(FileHandle root, ProjectConfig configuration,
                           SceneEditorDocument sceneA, SceneEditorDocument sceneB,
                           SceneHudAssociationService service,
                           ArrayList<String> invalidations,
                           ArrayList<String> notifications) {
        private games.pixscape.studio.configuration.SceneMeta metaA() {
            return configuration.getSceneMeta("Scene A");
        }
        private games.pixscape.studio.configuration.SceneMeta metaB() {
            return configuration.getSceneMeta("Scene B");
        }
    }
}
