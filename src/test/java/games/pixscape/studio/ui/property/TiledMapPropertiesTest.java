package games.pixscape.studio.ui.property;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.commands.SetSpatialBlockPhysicsCollisionCommand;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import games.pixscape.studio.ui.widget.SimpleTextField;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class TiledMapPropertiesTest {
    private static ProjectConfig previousConfig;
    private final EventFlow.SubscriptionScope subscriptions = EventFlow.i().newSubscriptionScope(() -> true);

    @After
    public void clearEvents() {
        subscriptions.close();
        EventFlow.i().discardPending();
    }

    @BeforeClass
    public static void loadUi() {
        previousConfig = ProjectConfig.getInstance();
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Map Properties");
        config.getCurrentSceneMeta().physicsEnabled = true;
        ProjectConfig.setInstance(config);
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadUi() {
        VisUiTestBootstrap.unloadSkin();
        ProjectConfig.setInstance(previousConfig);
    }

    @Test
    public void nameCommitsThroughKeyboardHistoryWithoutChangingStableId() {
        Fixture fixture = new Fixture();
        Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class[]{Batch.class},
                (proxy, method, args) -> null);
        Stage stage = new Stage(new ScreenViewport(), batch);
        try {
            int map = fixture.addMap(0, 16, 100);
            int other = fixture.addMap(0, 16, 100);
            PixscapeIdentityComponent identity = fixture.world.getMapper(PixscapeIdentityComponent.class).create(map);
            identity.name = "";
            identity.stableId = 123456789;
            PixscapeIdentityComponent otherIdentity = fixture.world.getMapper(PixscapeIdentityComponent.class).create(other);
            otherIdentity.name = "Other";
            otherIdentity.stableId = 42;
            TiledMapProperties properties = fixture.properties();
            properties.setMapEntityId(map);
            stage.addActor(properties);
            SimpleTextField name = properties.findActor("tiledMapName");
            VisLabel id = properties.findActor("tiledMapStableId");
            assertEquals("", name.getText());
            assertEquals("123456789", id.getText().toString());
            assertFalse(fixture.history.isDirty());
            assertEquals(0, fixture.saveRequests);
            assertFalse(fixture.history.canUndo());

            name.setText("MonSuperNom");
            stage.setKeyboardFocus(name);
            assertTrue(stage.keyDown(Input.Keys.ENTER));
            EventFlow.i().flush();
            assertEquals("MonSuperNom", identity.name);
            assertEquals(0, fixture.saveRequests);
            assertTrue(fixture.history.isDirty());
            fixture.history.undo();
            EventFlow.i().flush();
            assertEquals("", name.getText());
            assertFalse(fixture.history.isDirty());
            fixture.history.redo();
            EventFlow.i().flush();
            assertEquals("MonSuperNom", name.getText());
            assertEquals(123456789, identity.stableId);
            assertEquals("Other", otherIdentity.name);
            assertEquals(42, otherIdentity.stableId);

            long revision = fixture.history.getMutationRevision();
            name.commit();
            assertEquals(revision, fixture.history.getMutationRevision());
            assertEquals(0, fixture.saveRequests);
            properties.setMapEntityId(other);
            assertEquals("Other", name.getText());
            assertEquals("42", id.getText().toString());
        } finally {
            stage.dispose();
            fixture.world.dispose();
        }
    }

    @Test
    public void absentIdentityAndAbsentStableIdStayAbsentWhenInspectedOrRenamed() {
        Fixture fixture = new Fixture();
        try {
            int map = fixture.addMap(0, 16, 100);
            var identities = fixture.world.getMapper(PixscapeIdentityComponent.class);
            TiledMapProperties properties = fixture.properties();
            properties.setMapEntityId(map);
            SimpleTextField name = properties.findActor("tiledMapName");
            VisLabel id = properties.findActor("tiledMapStableId");
            assertFalse(identities.has(map));
            assertEquals("", name.getText());
            assertEquals("Not assigned", id.getText().toString());
            assertFalse(fixture.history.canUndo());

            name.setText("Named without ID");
            name.commit();
            assertEquals("Named without ID", identities.get(map).name);
            assertEquals(-1, identities.get(map).stableId);
            fixture.history.undo();
            EventFlow.i().flush();
            assertEquals("", name.getText());
            fixture.history.redo();
            EventFlow.i().flush();
            assertEquals("Named without ID", name.getText());
            assertEquals(-1, identities.get(map).stableId);

            identities.get(map).stableId = 0;
            identities.get(map).name = null;
            properties.setMapEntityId(map);
            assertEquals("", name.getText());
            assertEquals("Not assigned", id.getText().toString());
            name.setText("Renamed");
            name.commit();
            assertEquals(0, identities.get(map).stableId);
            properties.setMapEntityId(-1);
            assertTrue(name.isDisabled());
        } finally {
            fixture.world.dispose();
        }
    }

    @Test
    public void selectingEitherMapBindsTheActualMapAndShowsMapControls() {
        Fixture fixture = new Fixture();
        int mapA = fixture.addMap(0, 16, 8);
        int mapB = fixture.addMap(0, 32, 12);
        fixture.tiled(mapB).spatialEnabled = true;
        fixture.tiled(mapB).data.spatialEnabled = true;

        TiledMapProperties properties = fixture.properties();
        properties.setMapEntityId(mapA);

        assertEquals(mapA, properties.mapEntityId());
        assertNotNull(properties.findActor("tiledMapCollisions"));
        assertNotNull(properties.findActor("tiledMapSpatialDepth"));
        assertFalse(checkBox(properties, "tiledMapCollisions").isChecked());
        assertFalse(checkBox(properties, "tiledMapSpatialDepth").isChecked());

        properties.setMapEntityId(mapB);

        assertEquals(mapB, properties.mapEntityId());
        assertFalse(checkBox(properties, "tiledMapCollisions").isChecked());
        assertTrue(checkBox(properties, "tiledMapSpatialDepth").isChecked());
    }

    @Test
    public void collisionAndSpatialDepthChangesTargetOnlyTheBoundMapAndAreUndoable() {
        Fixture fixture = new Fixture();
        int mapA = fixture.addMap(0, 16, 8);
        int mapB = fixture.addMap(0, 32, 12);
        TiledMapProperties properties = fixture.properties();
        properties.setMapEntityId(mapA);

        setChecked(properties, "tiledMapCollisions", true);
        setChecked(properties, "tiledMapSpatialDepth", true);

        assertTrue(fixture.world.getMapper(PhysicsBodyComponent.class).has(mapA));
        assertFalse(fixture.world.getMapper(PhysicsBodyComponent.class).has(mapB));
        assertTrue(fixture.tiled(mapA).spatialEnabled);
        assertTrue(fixture.tiled(mapA).data.spatialEnabled);
        assertFalse(fixture.tiled(mapB).spatialEnabled);
        assertFalse(fixture.layer.spatialEnabled);

        fixture.history.undo();
        assertFalse(fixture.tiled(mapA).spatialEnabled);
        assertTrue(fixture.world.getMapper(PhysicsBodyComponent.class).has(mapA));
        assertFalse(fixture.tiled(mapB).spatialEnabled);
        assertFalse(fixture.layer.spatialEnabled);

        fixture.history.redo();
        assertTrue(fixture.tiled(mapA).spatialEnabled);
        assertFalse(fixture.tiled(mapB).spatialEnabled);
        assertFalse(fixture.layer.spatialEnabled);
    }

    @Test
    public void controlsAreLeftAlignedAndSpatialDefaultsFollowSpatialDepth() {
        Fixture fixture = new Fixture();
        int map = fixture.addMap(0, 16, 8);
        TiledMapProperties properties = fixture.properties();
        properties.setMapEntityId(map);

        VisCheckBox collisions = checkBox(properties, "tiledMapCollisions");
        VisCheckBox spatial = checkBox(properties, "tiledMapSpatialDepth");
        CollapsibleVisTable defaults = properties.findActor("tiledMapSpatialDefaults");
        assertTrue((collisions.getLabel().getLabelAlign() & Align.left) != 0);
        assertTrue((spatial.getLabel().getLabelAlign() & Align.left) != 0);
        assertTrue(defaults.isCollapsed());

        setChecked(properties, "tiledMapSpatialDepth", true);
        assertFalse(defaults.isCollapsed());

        setChecked(properties, "tiledMapSpatialDepth", false);
        assertTrue(defaults.isCollapsed());
    }

    @Test
    public void collisionsCanRemoveAndUndoLinkedSpatialBlockPhysics() {
        Fixture fixture = new Fixture();
        int map = fixture.addMap(0, 16, 8);
        fixture.addLinkedCollision(map, 7);
        fixture.history.clear();
        TiledMapProperties properties = fixture.properties();
        properties.setMapEntityId(map);

        assertTrue(checkBox(properties, "tiledMapCollisions").isChecked());
        setChecked(properties, "tiledMapCollisions", false);
        assertTrue(fixture.world.getMapper(PhysicsBodyComponent.class).has(map));
        assertTrue(checkBox(properties, "tiledMapCollisions").isChecked());

        properties.confirmRemoveCollisions(map);

        assertFalse(fixture.world.getMapper(PhysicsBodyComponent.class).has(map));
        assertFalse(checkBox(properties, "tiledMapCollisions").isChecked());

        fixture.history.undo();
        assertTrue(fixture.world.getMapper(PhysicsBodyComponent.class).has(map));
        PhysicsShapesComponent restored = fixture.world
                .getMapper(PhysicsShapesComponent.class).get(map);
        assertEquals(1, restored.shapes.size);
        assertEquals(7, restored.shapes.first().spatialBlockId);
    }

    @Test
    public void scenePhysicsOffRejectsCollisionEnableWithoutChangingAuthoredState() {
        Fixture fixture = new Fixture();
        int map = fixture.addMap(0, 16, 8);
        TiledMapProperties properties = fixture.properties();
        properties.setMapEntityId(map);
        ProjectConfig.getInstance().getCurrentSceneMeta().physicsEnabled = false;
        try {
            setChecked(properties, "tiledMapCollisions", true);

            assertFalse(fixture.world.getMapper(PhysicsBodyComponent.class).has(map));
            assertFalse(ProjectConfig.getInstance().getCurrentSceneMeta().physicsEnabled);
            assertEquals(0, fixture.history.getCursor());
        } finally {
            ProjectConfig.getInstance().getCurrentSceneMeta().physicsEnabled = true;
        }
    }

    private static VisCheckBox checkBox(TiledMapProperties properties, String name) {
        return properties.findActor(name);
    }

    private static void setChecked(TiledMapProperties properties,
                                   String name,
                                   boolean checked) {
        VisCheckBox box = checkBox(properties, name);
        box.setChecked(checked);
        box.fire(new ChangeListener.ChangeEvent());
    }

    private final class Fixture {
        int saveRequests;
        final World world = new World(new WorldConfiguration());
        final HistoryManager history = new HistoryManager(16);
        final PhysicsService physics = new PhysicsService(world, null, new SceneMeta());
        final int layerEntity = world.create();
        final LayerComponent layer = world.getMapper(LayerComponent.class)
                .create(layerEntity);

        Fixture() {
            layer.layerIndex = 0;
            layer.spatialEnabled = false;
        }

        int addMap(int layerIndex, int tileHeight, int mapWidth) {
            int entityId = world.create();
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class)
                    .create(entityId);
            index.layerIndex = layerIndex;
            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class)
                    .create(entityId);
            tiled.projection = TiledProjection.ORTHO;
            tiled.tileWidth = 16;
            tiled.tileHeight = tileHeight;
            tiled.mapWidthCells = mapWidth;
            tiled.mapHeightCells = 6;
            tiled.chunkSize = 4;
            tiled.data = tiled.createMapData();
            return entityId;
        }

        TiledLayerComponent tiled(int entityId) {
            return world.getMapper(TiledLayerComponent.class).get(entityId);
        }

        void addLinkedCollision(int mapEntityId, int blockId) {
            TiledLayerComponent tiled = tiled(mapEntityId);
            tiled.data.setTile(1, 1, 1);
            SpatialBlockData block = new SpatialBlockData();
            block.id = blockId;
            block.structureId = blockId;
            block.x = 1f;
            block.y = 1f;
            block.width = 1f;
            block.depth = 1f;
            block.beginAuthoredLinkedTileRefs();
            block.addLinkedTileRef(1, 1, 1);
            world.getMapper(SpatialBlocksComponent.class)
                    .create(mapEntityId).blocks.add(block);
            history.execute(new SetSpatialBlockPhysicsCollisionCommand(
                    world,
                    history.historyIds(),
                    null,
                    physics,
                    mapEntityId,
                    blockId,
                    true));
            PhysicsShapeData linked = world.getMapper(PhysicsShapesComponent.class)
                    .get(mapEntityId).shapes.first();
            assertEquals(blockId, linked.spatialBlockId);
        }

        TiledMapProperties properties() {
            TiledMapProperties[] result = new TiledMapProperties[1];
            subscriptions.run(() -> result[0] = new TiledMapProperties(
                    world, history, physics, () -> saveRequests++));
            return result[0];
        }
    }
}
