package games.pixscape.studio.ui.property;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlockOrientation;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class SpatialBlockPropertiesTest {
    @BeforeClass
    public static void loadVisUiSkin() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadVisUiSkin() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void canBeConstructedWithNoSelection() {
        Fixture fixture = fixture();

        SpatialBlockProperties properties = new SpatialBlockProperties(
                fixture.world,
                fixture.history,
                fixture.selection,
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
        Assert.assertEquals(layerId, fixture.selection.getEditingLayerEntityId());
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
        Assert.assertEquals(SpatialBlockSelectionService.NO_LAYER, fixture.selection.getEditingLayerEntityId());
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, fixture.selection.getSelectedBlockId());
    }

    private static Fixture fixture() {
        World world = new World(new WorldConfiguration());
        return new Fixture(world, new HistoryManager(8), new SpatialBlockSelectionService());
    }

    private static SpatialBlockData block(int id) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = id;
        block.name = "Block " + id;
        block.enabled = true;
        block.width = 1f;
        block.depth = 1f;
        block.height = 8f;
        block.orientation = SpatialBlockOrientation.TILE_CELL;
        block.actorOccluder = true;
        return block;
    }

    private record Fixture(World world, HistoryManager history, SpatialBlockSelectionService selection) {
        SpatialBlockProperties properties() {
            return new SpatialBlockProperties(world, history, selection, () -> {
            });
        }
    }
}
