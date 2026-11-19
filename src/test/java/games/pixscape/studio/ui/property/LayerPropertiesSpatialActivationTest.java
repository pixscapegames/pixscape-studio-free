package games.pixscape.studio.ui.property;

import com.artemis.World;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.Test;

import static org.junit.Assert.*;

public class LayerPropertiesSpatialActivationTest {
    @Test
    public void cancelEscapeAndConfirmationUseOneAtomicHistoryEntry() throws Exception {
        VisUiTestBootstrap.loadSkin();
        var batch = (com.badlogic.gdx.graphics.g2d.Batch) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{com.badlogic.gdx.graphics.g2d.Batch.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getProjectionMatrix", "getTransformMatrix" -> new com.badlogic.gdx.math.Matrix4();
                    case "getColor" -> com.badlogic.gdx.graphics.Color.WHITE;
                    case "isDrawing", "isBlendingEnabled" -> false;
                    default -> null;
                });
        var stage = new com.badlogic.gdx.scenes.scene2d.Stage(new com.badlogic.gdx.utils.viewport.ScreenViewport(), batch);
        World world = new World();
        try {
            HistoryManager history = new HistoryManager(16);
            IdentityRegistry ids = new IdentityRegistry();
            ids.bind(world, new games.pixscape.runtime.loading.SceneMetaRuntime());
            LayerService layers = new LayerService(world, null, history.historyIds(), ids);
            int layer = layers.addLayerTop("Actors");
            var lc = world.getMapper(LayerComponent.class).get(layer);
            lc.spatialEnabled = true;
            int actor = world.create();
            world.getMapper(games.pixscape.runtime.component.EntityIndexComponent.class).create(actor).layerIndex = 0;
            var footprints = world.getMapper(games.pixscape.runtime.component.spatial.SpatialFootprintComponent.class);
            footprints.create(actor).radiusPx = 19.25f;
            world.process();
            int[] saves = {0};
            LayerProperties panel = new LayerProperties(world, history, layers, () -> saves[0]++);
            stage.addActor(panel);
            panel.setLayerEntityId(layer);
            VisCheckBox checkbox = (VisCheckBox) field(panel, "spatialCheckBox");
            for (int attempt = 0; attempt < 3; attempt++) {
                checkbox.setChecked(false);
                var dialog = (games.pixscape.studio.ui.modal.StudioDialog) stage.getRoot().findActor("disable-layer-spatial-dialog");
                assertNotNull(dialog);
                assertEquals("Disable Spatial", dialog.getTitleLabel().getText().toString());
                assertEquals(2, dialog.getButtonsTable().getChildren().size);
                var cancel = (com.kotcrab.vis.ui.widget.VisTextButton) dialog.getButtonsTable().getChildren().get(0);
                var ok = (com.kotcrab.vis.ui.widget.VisTextButton) dialog.getButtonsTable().getChildren().get(1);
                assertEquals("Cancel", cancel.getText().toString());
                assertEquals("OK", ok.getText().toString());
                assertTrue(checkbox.isChecked());
                assertTrue(lc.spatialEnabled);
                assertEquals(19.25f, footprints.get(actor).radiusPx, 0);
                assertFalse(history.canUndo());
                if (attempt == 0) {
                    cancel.fire(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent());
                } else if (attempt == 1) {
                    stage.keyDown(com.badlogic.gdx.Input.Keys.ESCAPE);
                } else {
                    ok.fire(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent());
                }
                // VisDialog.hide sequences fade, listener removal, then actor removal.
                // Advance those native actions without sleeps or retrying the user action.
                stage.act(com.kotcrab.vis.ui.widget.VisDialog.FADE_TIME);
                stage.act(0);
                stage.act(0);
                assertNull("The popup must actually close", dialog.getStage());
                if (attempt < 2) {
                    assertTrue(lc.spatialEnabled);
                    assertTrue(checkbox.isChecked());
                    assertTrue(footprints.has(actor));
                    assertFalse(history.canUndo());
                    assertEquals(0, saves[0]);
                }
            }
            assertFalse(lc.spatialEnabled);
            assertFalse(footprints.has(actor));
            assertFalse(checkbox.isChecked());
            assertEquals(1, history.getCursor());
            assertEquals(1, saves[0]);
            history.undo();
            assertTrue(lc.spatialEnabled);
            assertEquals(19.25f, footprints.get(actor).radiusPx, 0);
        } finally {
            stage.dispose();
            world.dispose();
            VisUiTestBootstrap.unloadSkin();
            games.pixscape.studio.event.EventFlow.i().discardPending();
        }
    }

    @Test
    public void physicsFreeLayerCanBeActivatedReactivatedAndRemainsUnique() throws Exception {
        ProjectConfig previous = ProjectConfig.getInstance();
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Spatial without Physics");
        ProjectConfig.setInstance(config);
        World world = new World();
        VisUiTestBootstrap.loadSkin();
        try {
            assertFalse(config.getCurrentSceneMeta().physicsEnabled);
            HistoryManager history = new HistoryManager(16);
            IdentityRegistry identities = new IdentityRegistry();
            identities.bind(world, config.getCurrentSceneMeta());
            LayerService layers = new LayerService(world, null, history.historyIds(), identities);
            int first = layers.addLayerTop("First");
            int second = layers.addLayerTop("Second");
            world.process();
            LayerProperties properties = new LayerProperties(world, history, layers, () -> { });
            VisCheckBox checkbox = (VisCheckBox) field(properties, "spatialCheckBox");
            CollapsibleVisTable section = (CollapsibleVisTable) field(properties, "spatialSection");
            properties.setLayerEntityId(first);
            assertFalse(section.isCollapsed());
            for (int cycle = 0; cycle < 2; cycle++) {
                checkbox.setChecked(true);
                assertTrue(world.getMapper(LayerComponent.class).get(first).spatialEnabled);
                properties.setLayerEntityId(second);
                assertTrue(section.isCollapsed());
                int cursor = history.getCursor();
                // A stale or programmatic request must still preserve the single-layer rule.
                checkbox.setChecked(true);
                assertFalse(world.getMapper(LayerComponent.class).get(second).spatialEnabled);
                assertEquals(cursor, history.getCursor());
                properties.setLayerEntityId(first);
                checkbox.setChecked(false);
                assertFalse(world.getMapper(LayerComponent.class).get(first).spatialEnabled);
                assertFalse(section.isCollapsed());
            }
            history.undo();
            assertTrue(world.getMapper(LayerComponent.class).get(first).spatialEnabled);
            history.redo();
            assertFalse(world.getMapper(LayerComponent.class).get(first).spatialEnabled);
            assertFalse(config.getCurrentSceneMeta().physicsEnabled);
        } finally {
            world.dispose();
            VisUiTestBootstrap.unloadSkin();
            ProjectConfig.setInstance(previous);
            games.pixscape.studio.event.EventFlow.i().discardPending();
        }
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
