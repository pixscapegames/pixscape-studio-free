package games.pixscape.studio.ui.property;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.ui.widget.SimpleFloatField;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class SpatialBlockPropertiesTest {
    private static ProjectConfig previousConfig;

    @BeforeClass
    public static void loadVisUiSkin() {
        previousConfig = ProjectConfig.getInstance();
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Properties");
        config.getCurrentSceneMeta().pixelsPerMeter = 32f;
        ProjectConfig.setInstance(config);
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadVisUiSkin() {
        VisUiTestBootstrap.unloadSkin();
        ProjectConfig.setInstance(previousConfig);
    }

    @Test
    public void canBeConstructedWithNoSelection() {
        Fixture fixture = fixture();

        SpatialBlockProperties properties = new SpatialBlockProperties(
                fixture.world,
                fixture.history,
                fixture.selection,
                fixture.physics,
                () -> {
                }
        );

        Assert.assertFalse(properties.hasValidSelection());
        Assert.assertNull(properties.activeBlock());
    }

    @Test
    public void activeBlockReturnsNullWhenLayerEntityIsMinusOne() {
        Fixture fixture = fixture();
        SpatialBlockProperties properties = fixture.properties();

        properties.setSpatialBlock(-1, 1);

        Assert.assertEquals(-1, properties.activeLayerEntity());
        Assert.assertNull(properties.activeComponent());
        Assert.assertNull(properties.activeBlock());
    }

    @Test
    public void activeBlockReturnsNullWhenBlockIdIsMinusOne() {
        Fixture fixture = fixture();
        int layerId = fixture.world.create();
        fixture.world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockProperties properties = fixture.properties();

        properties.setSpatialBlock(layerId, -1);

        Assert.assertFalse(properties.hasValidSelection());
        Assert.assertNull(properties.activeComponent());
        Assert.assertNull(properties.activeBlock());
    }

    @Test
    public void activeBlockReturnsNullWhenComponentIsMissing() {
        Fixture fixture = fixture();
        int layerId = fixture.world.create();
        SpatialBlockProperties properties = fixture.properties();

        properties.setSpatialBlock(layerId, 1);

        Assert.assertFalse(properties.hasValidSelection());
        Assert.assertNull(properties.activeComponent());
        Assert.assertNull(properties.activeBlock());
    }

    @Test
    public void deletingSelectedBlockClearsSelectionAndLeavesPropertiesSafe() {
        Fixture fixture = fixture();
        int layerId = fixture.world.create();
        SpatialBlocksComponent component = fixture.world.getMapper(SpatialBlocksComponent.class).create(layerId);
        component.blocks.add(block(7));
        fixture.selection.selectBlock(layerId, 7);

        SpatialBlockProperties properties = fixture.properties();
        properties.setSpatialBlock(layerId, 7);
        Assert.assertNotNull(properties.activeBlock());

        component.blocks.clear();
        properties.refreshNow();

        Assert.assertNull(properties.activeBlock());
        Assert.assertEquals(layerId, fixture.selection.getEditingMapEntityId());
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, fixture.selection.getSelectedBlockId());
    }

    @Test
    public void deletedLayerClearsSpatialSelectionAndLeavesPropertiesSafe() {
        Fixture fixture = fixture();
        int layerId = fixture.world.create();
        SpatialBlocksComponent component = fixture.world.getMapper(SpatialBlocksComponent.class).create(layerId);
        component.blocks.add(block(3));
        fixture.selection.selectBlock(layerId, 3);

        SpatialBlockProperties properties = fixture.properties();
        properties.setSpatialBlock(layerId, 3);
        fixture.world.delete(layerId);
        fixture.world.process();

        properties.refreshNow();

        Assert.assertNull(properties.activeBlock());
        Assert.assertEquals(SpatialBlockSelectionService.NO_MAP, fixture.selection.getEditingMapEntityId());
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, fixture.selection.getSelectedBlockId());
    }

    @Test
    public void fractionalFootprintEditUsesHistoryAndPreservesLinkedRefs() {
        Fixture fixture = fixture();
        int layerId = tiledLayer(fixture.world);
        fixture.history.historyIds().ensureForEntity(layerId);
        SpatialBlockData wall = block(1);
        wall.structureId = 1;
        wall.x = 1f;
        wall.y = 1f;
        wall.beginAuthoredLinkedTileRefs();
        wall.addLinkedTileRef(1, 1, 1);
        fixture.world.getMapper(SpatialBlocksComponent.class).create(layerId).blocks.add(wall);
        fixture.selection.selectBlock(layerId, 1);
        SpatialBlockProperties properties = fixture.properties();
        properties.setSpatialBlock(layerId, 1);

        properties.submitFootprintEdit(1.18f, 1.42f, 0.67f, 0.24f);

        SpatialBlockData edited = properties.activeBlock();
        Assert.assertEquals(1.18f, edited.x, 0f);
        Assert.assertEquals(1.42f, edited.y, 0f);
        Assert.assertEquals(0.67f, edited.width, 0f);
        Assert.assertEquals(0.24f, edited.depth, 0f);
        Assert.assertEquals(1, edited.linkedTileRefs.size);
        fixture.history.undo();
        Assert.assertEquals(1f, properties.activeBlock().x, 0f);
        fixture.history.redo();
        Assert.assertEquals(1.18f, properties.activeBlock().x, 0f);
    }

    @Test
    public void outOfEnvelopePropertyEditIsRejectedWithoutMutation() {
        Fixture fixture = fixture();
        int layerId = tiledLayer(fixture.world);
        fixture.history.historyIds().ensureForEntity(layerId);
        SpatialBlockData wall = block(1);
        wall.structureId = 1;
        wall.x = 1f;
        wall.y = 1f;
        wall.beginAuthoredLinkedTileRefs();
        wall.addLinkedTileRef(1, 1, 1);
        fixture.world.getMapper(SpatialBlocksComponent.class).create(layerId).blocks.add(wall);
        fixture.selection.selectBlock(layerId, 1);
        SpatialBlockProperties properties = fixture.properties();
        properties.setSpatialBlock(layerId, 1);

        properties.submitFootprintEdit(0.9f, null, null, null);

        Assert.assertEquals(1f, properties.activeBlock().x, 0f);
        Assert.assertFalse(fixture.history.canUndo());
    }

    @Test
    public void attachedWallPropertiesRejectTranslationAndLockedEndButAllowSafeThickness() {
        Fixture fixture = fixture();
        int layerId = tiledLayer(fixture.world);
        fixture.history.historyIds().ensureForEntity(layerId);
        SpatialBlocksComponent walls = fixture.world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData selected = wall(1, 4, 0, 1, 3, 1);
        SpatialBlockData neighbor = wall(2, 4, 2, 0, 1, 3);
        walls.blocks.add(selected);
        walls.blocks.add(neighbor);
        fixture.selection.selectBlock(layerId, 1);
        SpatialBlockProperties properties = fixture.properties();
        properties.setSpatialBlock(layerId, 1);

        properties.submitFootprintEdit(0.1f, null, null, null);
        Assert.assertEquals(0f, properties.activeBlock().x, 0f);
        properties.submitFootprintEdit(null, null, 2.5f, null);
        Assert.assertEquals(3f, properties.activeBlock().width, 0f);
        Assert.assertFalse(fixture.history.canUndo());

        properties.submitFootprintEdit(null, null, null, 0.5f);
        Assert.assertEquals(0.5f, properties.activeBlock().depth, 0f);
        Assert.assertTrue(fixture.history.canUndo());
    }

    @Test
    public void panelUsesSpatialWallLabelsAndHasNoGlobalStateControls() {
        Fixture fixture = fixture();
        SpatialBlockProperties properties = fixture.properties();

        Assert.assertEquals("SPATIAL WALL",
                ((VisLabel) properties.findActor("spatialWallTitle")).getText().toString());
        Assert.assertTrue(hasText(properties, "Name (optional)"));
        Assert.assertTrue(hasText(properties, "Structure ID"));
        Assert.assertTrue(hasText(properties, "Structure altitude"));
        Assert.assertTrue(hasText(properties, "Structure height"));
        Assert.assertFalse(hasText(properties, "Enabled"));
        Assert.assertFalse(hasText(properties, "Orientation"));
        Assert.assertTrue(properties.findActor("spatialWallStructureId") instanceof VisLabel);
        Assert.assertTrue(properties.findActor("spatialWallPhysicsCollision")
                instanceof VisCheckBox);
    }

    @Test
    public void physicsCollisionCheckboxIsRelationDerivedAndUndoable() {
        Fixture fixture = fixture();
        int layerId = tiledLayer(fixture.world);
        TransformComponent transform =
                fixture.world.getMapper(TransformComponent.class).create(layerId);
        transform.scaleX = 1f;
        transform.scaleY = 1f;
        fixture.history.historyIds().ensureForEntity(layerId);
        SpatialBlockData wall = wall(1, 1, 1, 1, 1, 1);
        fixture.world.getMapper(SpatialBlocksComponent.class)
                .create(layerId).blocks.add(wall);
        fixture.selection.selectBlock(layerId, 1);
        SpatialBlockProperties properties = fixture.properties();
        properties.setSpatialBlock(layerId, 1);
        VisCheckBox collision = checkBox(
                properties, "spatialWallPhysicsCollision");
        Assert.assertFalse(collision.isChecked());

        setChecked(properties, "spatialWallPhysicsCollision", true);
        Assert.assertTrue(collision.isChecked());
        Assert.assertEquals(1, fixture.world.getMapper(
                PhysicsShapesComponent.class).get(layerId).shapes.size);
        Assert.assertFalse(java.util.Arrays.stream(
                        SpatialBlockData.class.getFields())
                .anyMatch(field -> field.getName().equals("physicsCollision")));

        fixture.history.undo();
        properties.refreshNow();
        Assert.assertFalse(collision.isChecked());

        fixture.history.redo();
        properties.refreshNow();
        Assert.assertTrue(collision.isChecked());

        setChecked(properties, "spatialWallPhysicsCollision", false);
        Assert.assertFalse(collision.isChecked());
        Assert.assertFalse(fixture.world.getMapper(
                PhysicsShapesComponent.class).has(layerId));
    }

    @Test
    public void structureAltitudeAndHeightEditsRemainAtomicAndUndoable() {
        Fixture fixture = fixture();
        int layerId = tiledLayer(fixture.world);
        fixture.history.historyIds().ensureForEntity(layerId);
        SpatialBlocksComponent walls = fixture.world.getMapper(SpatialBlocksComponent.class).create(layerId);
        walls.blocks.add(wall(1, 4, 0, 0, 2, 1));
        walls.blocks.add(wall(2, 4, 1, 0, 1, 2));
        fixture.selection.selectBlock(layerId, 1);
        SpatialBlockProperties properties = fixture.properties();
        properties.setSpatialBlock(layerId, 1);

        SimpleFloatField altitude = properties.findActor("spatialWallStructureAltitude");
        altitude.setText("12");
        altitude.commit();
        Assert.assertEquals(12f, walls.blocks.get(0).altitude, 0f);
        Assert.assertEquals(12f, walls.blocks.get(1).altitude, 0f);
        fixture.history.undo();
        Assert.assertEquals(0f, walls.blocks.get(0).altitude, 0f);
        Assert.assertEquals(0f, walls.blocks.get(1).altitude, 0f);
        fixture.history.redo();

        SimpleFloatField height = properties.findActor("spatialWallStructureHeight");
        height.setText("18");
        height.commit();
        Assert.assertEquals(18f, walls.blocks.get(0).height, 0f);
        Assert.assertEquals(18f, walls.blocks.get(1).height, 0f);
        fixture.history.undo();
        Assert.assertEquals(8f, walls.blocks.get(0).height, 0f);
        Assert.assertEquals(8f, walls.blocks.get(1).height, 0f);
    }

    private static VisCheckBox checkBox(SpatialBlockProperties properties, String name) {
        return properties.findActor(name);
    }

    private static void setChecked(SpatialBlockProperties properties, String name, boolean checked) {
        VisCheckBox box = checkBox(properties, name);
        box.setChecked(checked);
        box.fire(new ChangeListener.ChangeEvent());
    }

    private static boolean hasText(Actor actor, String expected) {
        if (actor instanceof VisLabel
                && expected.contentEquals(((VisLabel) actor).getText())) return true;
        if (actor instanceof VisCheckBox
                && expected.contentEquals(((VisCheckBox) actor).getText())) return true;
        if (!(actor instanceof Group)) return false;
        Group group = (Group) actor;
        for (int i = 0; i < group.getChildren().size; i++) {
            if (hasText(group.getChildren().get(i), expected)) return true;
        }
        return false;
    }

    private static Fixture fixture() {
        World world = new World(new WorldConfiguration());
        return new Fixture(
                world,
                new HistoryManager(8),
                new SpatialBlockSelectionService(),
                new PhysicsService(world, null, new SceneMeta()));
    }

    private static SpatialBlockData block(int id) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = id;
        block.name = "Block " + id;
        block.width = 1f;
        block.depth = 1f;
        block.height = 8f;
        block.actorOccluder = true;
        return block;
    }

    private static SpatialBlockData wall(int id, int structureId,
                                         int x, int y, int width, int depth) {
        SpatialBlockData wall = block(id);
        wall.structureId = structureId;
        wall.x = x;
        wall.y = y;
        wall.width = width;
        wall.depth = depth;
        wall.beginAuthoredLinkedTileRefs();
        for (int gy = y; gy < y + depth; gy++) {
            for (int gx = x; gx < x + width; gx++) wall.addLinkedTileRef(gx, gy, 1);
        }
        return wall;
    }

    private static int tiledLayer(World world) {
        int layerId = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layerId);
        tiled.data = new TiledMapLayerData(4, 4, 16, 16, 2, SceneMetaRuntime.TiledProjection.ORTHO);
        for (int gy = 0; gy < 4; gy++) for (int gx = 0; gx < 4; gx++) tiled.data.setTile(gx, gy, 1);
        return layerId;
    }

    private record Fixture(
            World world,
            HistoryManager history,
            SpatialBlockSelectionService selection,
            PhysicsService physics) {
        SpatialBlockProperties properties() {
            return new SpatialBlockProperties(world, history, selection, physics, () -> {
            });
        }
    }
}
