package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.GameObjectCompositionSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.studio.configuration.EditorSettings;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryIdRegistry;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class TiledEditorVisibilityTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void preferencesSurviveHierarchyEditsAndReloadWithoutMutatingSceneData() throws Exception {
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", temporary.getRoot().getAbsolutePath());
        var composition = new GameObjectCompositionSystem(new DynamicEntityRenderState());
        World world = new World(new WorldConfigurationBuilder()
                .with(new games.pixscape.runtime.system.DirtyTrackerSystem(32),
                        new GameObjectHierarchySystem(), composition).build());
        try {
            EditorSettings.load();
            assertFalse(EditorSettings.get().gridOnTop);
            SceneMeta scene = new SceneMeta("Scene", "scene1.json");
            IdentityRegistry ids = new IdentityRegistry();
            ids.bind(world, scene);
            LayerService layers = new LayerService(world, null, new HistoryIdRegistry(), ids);
            int sceneLayer = layers.getLayerEntity(layers.addLayerTop("Scene layer"));
            int map = node(world, ids, -1);
            var mapData = world.getMapper(TiledMapComponent.class).create(map);
            mapData.projection = games.pixscape.runtime.tiled.TiledProjection.ORTHO;
            mapData.tileWidth = mapData.tileHeight = 16;
            mapData.width = mapData.height = 2;
            int tile = node(world, ids, map);
            world.getMapper(TiledMapLayerComponent.class).create(tile);
            int objects = node(world, ids, map);
            world.getMapper(TiledMapLayerComponent.class).create(objects).kind = TiledMapLayerComponent.Kind.OBJECT;
            int object = node(world, ids, objects);
            var body = world.getMapper(PhysicsBodyComponent.class).create(object);
            int image = node(world, ids, map);
            world.getMapper(TiledMapLayerComponent.class).create(image).kind = TiledMapLayerComponent.Kind.IMAGE;
            String project = temporary.newFolder("project").getAbsolutePath();
            layers.bindEditorVisibility(project, scene.file);
            SelectionService selection = new SelectionService(world, layers);
            world.process();
            assertTrue(selection.isSelectableInPublishedViewport(object));
            assertFalse(layers.hasTiledVisibilityControl(object));
            for (int entity : new int[]{map, tile, objects, image}) {
                assertTrue(layers.hasTiledVisibilityControl(entity));
            }
            layers.setTiledEditorVisible(tile, false);
            layers.setTiledEditorVisible(map, false);
            world.process();
            assertFalse(selection.isSelectableInPublishedViewport(object));
            assertFalse(selection.isSelectableInPublishedViewport(image));
            layers.setTiledEditorVisible(image, false);
            layers.setTiledEditorVisible(map, true);
            world.process();
            assertTrue(selection.isSelectableInPublishedViewport(object));
            assertFalse(selection.isSelectableInPublishedViewport(tile));
            assertFalse(selection.isSelectableInPublishedViewport(image));
            assertSame(body, world.getMapper(PhysicsBodyComponent.class).get(object));
            assertTrue(world.getMapper(VisibilityComponent.class).get(tile).visible);
            layers.setLayerVisible(sceneLayer, false);
            world.process();
            assertFalse(selection.isSelectableInPublishedViewport(object));
            layers.setLayerVisible(sceneLayer, true);
            world.getMapper(VisibilityComponent.class).get(objects).visible = false;
            world.process();
            assertFalse(selection.isSelectableInPublishedViewport(object));
            world.getMapper(VisibilityComponent.class).get(objects).visible = true;
            ids.setName(tile, "Renamed");
            world.getMapper(EntityIndexComponent.class).get(tile).zIndex = 20;
            world.process();
            assertFalse(layers.isTiledEditorVisible(tile));
            int stable = ids.getStableId(tile);
            IdentityRegistry.unindexEntityImmediately(world, tile);
            world.delete(tile);
            world.process();
            int replacement = node(world, ids, map);
            world.getMapper(TiledMapLayerComponent.class).create(replacement);
            world.process();
            assertTrue(layers.isTiledEditorVisible(replacement));
            IdentityRegistry.unindexEntityImmediately(world, replacement);
            world.getMapper(PixscapeIdentityComponent.class).get(replacement).stableId = stable;
            ids.rebuild();
            world.process();
            assertFalse(layers.isTiledEditorVisible(replacement));
            EditorSettings.get().gridOnTop = true;
            EditorSettings.save();
            EditorSettings.load();
            assertTrue(EditorSettings.get().gridOnTop);
            layers.bindEditorVisibility(project, "scene2.json");
            assertTrue(layers.isTiledEditorVisible(replacement));
            layers.bindEditorVisibility(project + "-other", scene.file);
            assertTrue(layers.isTiledEditorVisible(replacement));
            scene.name = "Renamed scene";
            layers.bindEditorVisibility(project, scene.file);
            world.process();
            assertFalse(selection.isSelectableInPublishedViewport(replacement));
            if (java.io.File.separatorChar == '\\') {
                layers.bindEditorVisibility(project.toUpperCase(java.util.Locale.ROOT), scene.file);
                assertFalse(layers.isTiledEditorVisible(replacement));
            }
            composition.setVisibilityFilter(null); // Preview/Runtime worlds do not install the Studio filter.
            world.process();
            assertTrue(selection.isSelectableInPublishedViewport(replacement));
        } finally {
            world.dispose();
            System.setProperty("user.home", previousHome);
            EditorSettings.load();
        }
    }

    private static int node(World world, IdentityRegistry ids, int parent) {
        int entity = world.create();
        world.getMapper(GameObjectComponent.class).create(entity);
        world.getMapper(TransformComponent.class).create(entity);
        world.getMapper(EntityIndexComponent.class).create(entity).layerIndex = 0;
        world.getMapper(VisibilityComponent.class).create(entity);
        ids.ensureStableId(entity);
        if (parent >= 0) {
            world.getMapper(GameObjectMemberComponent.class).create(entity).parentStableId = ids.getStableId(parent);
        }
        return entity;
    }
}
