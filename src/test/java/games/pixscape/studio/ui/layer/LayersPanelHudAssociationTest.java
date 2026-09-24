package games.pixscape.studio.ui.layer;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.document.SceneEditorDocument;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.service.hud.HudDocumentPersistenceService;
import games.pixscape.studio.service.hud.HudScreenAssetAuthoringService;
import games.pixscape.studio.service.hud.SceneHudAssociationService;
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

public class LayersPanelHudAssociationTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private ProjectConfig previous;

    @After public void restoreConfiguration() {
        if (previous != null) ProjectConfig.setInstance(previous);
    }

    @Test public void projectionKeepsHudAboveWorldWithoutChangingWorldIndices() {
        Array<LayerService.LayerUI> layers = new Array<>();
        layers.add(new LayerService.LayerUI(10, "Bottom", "", 0,
                false, true, false));
        layers.add(new LayerService.LayerUI(20, "Top", "", 1,
                false, true, false));

        Array<LayersPanel.PanelEntry> entries = LayersPanel.entriesFor("hud/status", layers);

        assertEquals(3, entries.size);
        assertEquals(LayersPanel.EntryKind.HUD, entries.get(0).kind());
        assertEquals("hud/status", entries.get(0).hudScreenId());
        assertEquals(LayersPanel.EntryKind.WORLD, entries.get(1).kind());
        assertEquals(20, entries.get(1).worldLayer().layerEntityId());
        assertEquals(1, entries.get(1).worldLayer().index());
        assertEquals(10, entries.get(2).worldLayer().layerEntityId());
        assertEquals(0, entries.get(2).worldLayer().index());

        Array<LayersPanel.PanelEntry> worldOnly = LayersPanel.entriesFor(null, layers);
        assertEquals(2, worldOnly.size);
        for (LayersPanel.PanelEntry entry : worldOnly) {
            assertEquals(LayersPanel.EntryKind.WORLD, entry.kind());
        }
    }

    @Test public void selectedHudRemoveClearsOnlyAssociationAndUndoRestoresIt()
            throws Exception {
        previous = ProjectConfig.getInstance();
        ProjectConfig configuration = new ProjectConfig();
        configuration.createSceneMeta("Main");
        ProjectConfig.setInstance(configuration);
        FileHandle root = new FileHandle(temporary.newFolder());
        new HudScreenAssetAuthoringService().create(root, "status", 320, 180);

        SceneEditorContext context = new SceneEditorContext(
                "scene1", new StudioEditingModeService());
        SceneEditorDocument document = new SceneEditorDocument(
                "scene1", "Main", context);
        SceneHudAssociationService associations = new SceneHudAssociationService(
                () -> root, () -> configuration, new HudDocumentPersistenceService(),
                ignored -> {}, (sceneTag, screenId) -> {});
        assertTrue(associations.associate(document, "hud/status"));

        assertTrue(LayersPanel.removeSelectedHudIfCurrent(
                LayersPanel.EntryKind.HUD, "hud/status", "hud/status",
                () -> associations.remove(document)));
        assertNull(configuration.getSceneMeta("Main").defaultHudScreenId);

        context.historyManager().undo();
        assertEquals("hud/status", configuration.getSceneMeta("Main").defaultHudScreenId);
        assertFalse(LayersPanel.removeSelectedHudIfCurrent(
                LayersPanel.EntryKind.HUD, "hud/other", "hud/status",
                () -> associations.remove(document)));
        assertEquals("hud/status", configuration.getSceneMeta("Main").defaultHudScreenId);
        context.dispose();
    }

    @Test public void hudActivationUsesOnlyTheCanonicalCurrentAssociation() {
        List<String> opened = new ArrayList<>();

        assertFalse(LayersPanel.openSelectedHudIfCurrent(
                LayersPanel.EntryKind.WORLD, null, "hud/current", opened::add));
        assertFalse(LayersPanel.openSelectedHudIfCurrent(
                LayersPanel.EntryKind.HUD, "hud/stale", "hud/current", opened::add));
        assertTrue(LayersPanel.openSelectedHudIfCurrent(
                LayersPanel.EntryKind.HUD, "hud/current", "hud/current", opened::add));

        assertEquals(List.of("hud/current"), opened);
    }

    @Test public void worldReorderUsesStoredIndicesIndependentlyOfHudProjection() {
        World world = new World(new WorldConfiguration());
        try {
            IdentityRegistry identities = new IdentityRegistry();
            identities.bind(world, new SceneMetaRuntime());
            LayerService layers = new LayerService(world, null,
                    new games.pixscape.studio.history.HistoryIdRegistry(), identities);
            layers.addLayerTop("Bottom");
            layers.addLayerTop("Top");
            int bottom = layers.getLayerEntity(0);
            int top = layers.getLayerEntity(1);

            Array<LayersPanel.PanelEntry> entries = LayersPanel.entriesFor(
                    "hud/status", layers.getLayerUIs());
            assertEquals(0, layers.indexOfLayerEntity(bottom));
            assertEquals(1, layers.indexOfLayerEntity(top));
            assertEquals(1, entries.get(1).worldLayer().index());

            assertTrue(layers.moveLayer(1, 0));
            assertEquals(top, layers.getLayerEntity(0));
            assertEquals(bottom, layers.getLayerEntity(1));
        } finally {
            world.dispose();
        }
    }
}
