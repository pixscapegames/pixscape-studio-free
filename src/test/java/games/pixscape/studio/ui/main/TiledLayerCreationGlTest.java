package games.pixscape.studio.ui.main;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.kotcrab.vis.ui.widget.VisLabel;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TiledMapComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.ui.asset.AssetNode;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.commands.AddTiledMapCommand;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.ui.tree.ItemTreePanel;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static games.pixscape.studio.ui.main.ResourceAssetInspectorGlTest.*;
import static org.junit.Assert.*;

public class TiledLayerCreationGlTest {
    @Test
    public void mapMenuCreatesEditableLayerWithUndoRedoAndPersistedOwnership() throws Exception {
        withStudio(900, (studio, root) -> {
            open(studio, project(studio, root.child("project")));
            var context = studio.getSceneEditorContext();
            var history = context.historyManager();
            AtomicInteger first = new AtomicInteger(-1);
            history.execute(new AddTiledMapCommand(context.layerService(), context.layerService().getLayerEntity(0),
                    12, 8, TiledProjection.ORTHO, 24, 16, 16, first::set));
            studio.render();
            int mapStable = context.world().getMapper(GameObjectMemberComponent.class).get(first.get()).parentStableId;
            int map = context.identityRegistry().findByStableId(mapStable);
            context.selectionService().selectOnly(map, SelectionService.SelectionSource.TREE);
            studio.render();
            VisLabel gridWidth = studio.getUiStage().getRoot().findActor("tiledGridWidth");
            VisLabel gridHeight = studio.getUiStage().getRoot().findActor("tiledGridHeight");
            assertNotNull("Grid dimensions belong to the map inspector", gridWidth);
            assertEquals("12", gridWidth.getText().toString());
            assertEquals("8", gridHeight.getText().toString());

            // Exercise the real Items popup through Stage listeners, without claiming a native mouse gesture.
            var show = ItemTreePanel.class.getDeclaredMethod("showGameObjectContextMenu",
                    int.class, AssetNode.class, Stage.class, float.class, float.class);
            show.setAccessible(true);
            show.invoke(studio.getItemTreePanel(), map, null, studio.getUiStage(), 200f, 400f);
            MenuItem add = null;
            for (var actor : studio.getUiStage().getActors()) {
                if (actor instanceof PopupMenu menu) {
                    for (var child : menu.getChildren()) {
                        if (child instanceof MenuItem item && "Add tile layer".contentEquals(item.getText())) {
                            add = item;
                        }
                    }
                }
            }
            assertNotNull("The map context menu must expose tile layer creation", add);
            int cursor = history.getCursor();
            for (var listener : add.getListeners()) {
                if (listener instanceof ClickListener click) {
                    click.clicked(new InputEvent(), 0f, 0f);
                }
            }
            studio.render();
            assertEquals(cursor + 1, history.getCursor());
            int layer = context.selectionService().getTiledMapEditingTargetEntityId();
            assertTrue(layer >= 0);
            assertNotEquals(first.get(), layer);
            int stable = context.identityRegistry().ensureStableId(layer);
            for (int cycle = 0; cycle < 3; cycle++) {
                history.undo();
                studio.render();
                assertEquals(-1, context.selectionService().getTiledMapEditingTargetEntityId());
                history.redo();
                studio.render();
                layer = context.selectionService().getTiledMapEditingTargetEntityId();
                assertEquals(stable, context.identityRegistry().ensureStableId(layer));
                assertEquals(mapStable, context.world().getMapper(GameObjectMemberComponent.class).get(layer).parentStableId);
            }
            assertTrue(context.isDirty());
            var file = root.child("saved-scene.json");
            SceneService.saveScene(context.world(), file, false);
            World loaded = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
            try {
                var identities = new IdentityRegistry();
                identities.bind(loaded, ProjectConfig.getInstance().getCurrentSceneMeta());
                SceneLoader.loadScene(loaded, file, false, ProjectConfig.getInstance().getCurrentSceneMeta());
                loaded.process();
                int restored = identities.findByStableId(stable);
                assertTrue(restored >= 0);
                assertEquals(mapStable, loaded.getMapper(GameObjectMemberComponent.class).get(restored).parentStableId);
                assertTrue(loaded.getMapper(TiledMapComponent.class).has(identities.findByStableId(mapStable)));
                assertEquals(12, loaded.getMapper(TiledLayerComponent.class).get(restored).mapWidthCells);
            } finally {
                loaded.dispose();
            }
        });
    }
}
