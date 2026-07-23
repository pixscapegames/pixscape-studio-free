package games.pixscape.studio.ui.property.entityproperties.physics;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.IconResolver;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.service.physics.SpatialOwnedFixtureSupport;
import games.pixscape.studio.service.tiled.TiledAllocatorService;
import games.pixscape.studio.ui.property.entityproperties.EntityPropertiesContext;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.FloatField;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

public class FixturesPanelSpatialOwnershipTest {

    @BeforeClass
    public static void loadSkin() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadSkin() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void ownedFixtureLocksGeometryBeforeCommitButKeepsPropertiesEditable() throws Exception {
        FixturePanelFixture setup = fixturePanel(true);
        FixturesPanel panel = setup.panel;

        VisSelectBox type = field(panel, "shapeBox", VisSelectBox.class);
        FloatField offsetX = field(panel, "offsetXWUField", FloatField.class);
        FloatField offsetY = field(panel, "offsetYWUField", FloatField.class);
        FloatField width = field(panel, "widthWUField", FloatField.class);
        VisCheckBox sensor = field(panel, "sensorBox", VisCheckBox.class);
        FloatField friction = field(panel, "frictionField", FloatField.class);
        VisTextButton autoSize = field(panel, "autoSizeBtn", VisTextButton.class);
        VisTextButton delete = field(panel, "deleteFixtureBtn", VisTextButton.class);
        VisLabel managed = field(panel, "spatialManagedLabel", VisLabel.class);

        assertTrue(type.isDisabled());
        assertTrue(offsetX.isDisabled());
        assertTrue(offsetY.isDisabled());
        assertTrue(width.isDisabled());
        assertTrue(autoSize.isDisabled());
        assertFalse(delete.isDisabled());
        assertFalse(offsetX.getText().isEmpty());
        assertFalse(offsetY.getText().isEmpty());
        assertSame(managed, field(panel, "spatialManagedNotice", Container.class).getActor());
        assertFalse(sensor.isDisabled());
        assertFalse(friction.isDisabled());

        type.setSelected("Circle");
        offsetX.setText("123");
        FocusListener.FocusEvent focusLost = new FocusListener.FocusEvent();
        focusLost.setType(FocusListener.FocusEvent.Type.keyboard);
        focusLost.setFocused(false);
        offsetX.fire(focusLost);
        offsetY.setText("456");
        offsetY.commit();

        assertEquals(0, setup.history.getCursor());
        assertEquals(FixtureDefData.SHAPE_BOX, setup.fixture().shapeType);
        assertEquals(0.25f, setup.fixture().offsetX, 0f);
        assertEquals(-0.5f, setup.fixture().offsetY, 0f);
        assertFalse(setup.history.isDirty());
    }

    @Test
    public void ownedFixtureSensorAndFrictionUseNormalHistoryUndoRedo() throws Exception {
        FixturePanelFixture setup = fixturePanel(true);
        VisCheckBox sensor = field(setup.panel, "sensorBox", VisCheckBox.class);
        FloatField friction = field(setup.panel, "frictionField", FloatField.class);

        sensor.setChecked(true);
        assertTrue(setup.fixture().isSensor);
        assertEquals(1, setup.history.getCursor());

        setup.history.undo();
        assertFalse(setup.fixture().isSensor);
        setup.history.redo();
        assertTrue(setup.fixture().isSensor);

        friction.setText("0.75");
        friction.commit();
        assertEquals(0.75f, setup.fixture().friction, 0f);
        assertEquals(2, setup.history.getCursor());
    }

    @Test
    public void deletingSelectedFixturePreservesBodyContextAndDisablesDeletedFixtureFields() throws Exception {
        FixturePanelFixture setup = fixturePanel(false);
        setup.selectionService.selectOnly(setup.bodyEntityId);

        setup.history.execute(new games.pixscape.studio.history.commands.DeleteFixtureCommand(
                setup.world,
                setup.history.historyIds(),
                setup.physicsSelection,
                setup.bodyEntityId,
                setup.fixtureId));
        setup.panel.refreshNow();

        assertNull(setup.fixture());
        assertEquals(PhysicsSelectionService.NO_FIXTURE,
                setup.physicsSelection.getSelectedFixtureId());
        assertTrue(setup.physicsSelection.isFocusedBody(setup.bodyEntityId));
        assertEquals(1, setup.selectionService.getSelectionSnapshot().size);
        assertTrue(field(setup.panel, "shapeBox", VisSelectBox.class).isDisabled());
        assertTrue(field(setup.panel, "offsetXWUField", FloatField.class).isDisabled());
        assertTrue(field(setup.panel, "frictionField", FloatField.class).isDisabled());

        setup.history.undo();
        assertNotNull(setup.fixture());
        assertEquals(PhysicsSelectionService.NO_FIXTURE,
                setup.physicsSelection.getSelectedFixtureId());
    }

    @Test
    public void ownedNoticeUsesFullPropertiesWidthAndWraps() throws Exception {
        FixturePanelFixture setup = fixturePanel(true);
        VisLabel label = field(setup.panel, "spatialManagedLabel", VisLabel.class);
        Container<?> notice = field(setup.panel, "spatialManagedNotice", Container.class);
        Cell<?> cell = field(setup.panel, "spatialManagedNoticeCell", Cell.class);

        assertSame(label, notice.getActor());
        assertEquals(3, cell.getColspan().intValue());
        assertEquals(1, cell.getExpandX().intValue());
        assertEquals(1f, cell.getFillX(), 0f);
        assertTrue(label.getWrap());
    }

    @Test
    public void customFixtureNoticeReservesNoLayoutSpace() throws Exception {
        FixturePanelFixture setup = fixturePanel(false);
        VisLabel label = field(setup.panel, "spatialManagedLabel", VisLabel.class);
        Container<?> notice = field(setup.panel, "spatialManagedNotice", Container.class);
        Cell<?> cell = field(setup.panel, "spatialManagedNoticeCell", Cell.class);

        assertNull(notice.getActor());
        assertNull(label.getParent());
        assertEquals(0f, notice.getMinHeight(), 0f);
        assertEquals(0f, notice.getPrefHeight(), 0f);
        assertEquals(0f, cell.getPadTop(), 0f);
        assertEquals(0f, cell.getPadBottom(), 0f);
    }

    @Test
    public void ownedCustomOwnedSelectionReusesAndRelayoutsTheNotice() throws Exception {
        FixturePanelFixture setup = fixturePanel(true);
        VisLabel label = field(setup.panel, "spatialManagedLabel", VisLabel.class);
        Container<?> notice = field(setup.panel, "spatialManagedNotice", Container.class);
        Cell<?> cell = field(setup.panel, "spatialManagedNoticeCell", Cell.class);
        CollapsibleVisTable details = field(setup.panel, "detailsBlock", CollapsibleVisTable.class);
        int rows = details.content().getRows();
        FixtureDefData custom = fixture(41, FixtureDefData.SHAPE_BOX);
        setup.fixtures.fixtures.add(custom);

        assertSame(label, notice.getActor());

        setup.physicsSelection.setSelectedFixture(setup.bodyEntityId, custom.fixtureId);
        setup.panel.refreshNow();

        assertFalse(field(setup.panel, "shapeBox", VisSelectBox.class).isDisabled());
        assertFalse(field(setup.panel, "offsetXWUField", FloatField.class).isDisabled());
        assertFalse(field(setup.panel, "offsetYWUField", FloatField.class).isDisabled());
        assertFalse(field(setup.panel, "widthWUField", FloatField.class).isDisabled());
        assertNull(notice.getActor());
        assertEquals(0f, notice.getPrefHeight(), 0f);
        assertEquals(0f, cell.getPadTop(), 0f);
        assertEquals(rows, details.content().getRows());

        setup.physicsSelection.setSelectedFixture(setup.bodyEntityId, setup.fixtureId);
        setup.panel.refreshNow();

        assertTrue(field(setup.panel, "shapeBox", VisSelectBox.class).isDisabled());
        assertTrue(field(setup.panel, "offsetXWUField", FloatField.class).isDisabled());
        assertSame(label, notice.getActor());
        assertSame(notice, label.getParent());
        assertEquals(2f, cell.getPadTop(), 0f);
        assertEquals(rows, details.content().getRows());
    }

    private static FixturePanelFixture fixturePanel(boolean owned) {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(16);
        PhysicsSelectionService physicsSelection = new PhysicsSelectionService();
        int bodyEntityId = world.create();
        history.historyIds().ensureForEntity(bodyEntityId);
        world.getMapper(PhysicsBodyComponent.class).create(bodyEntityId);
        PhysicsFixturesComponent fixtures =
                world.getMapper(PhysicsFixturesComponent.class).create(bodyEntityId);

        int blockId = 7;
        FixtureDefData fixture = fixture(
                SpatialOwnedFixtureSupport.fixtureIdForBlock(blockId),
                FixtureDefData.SHAPE_BOX
        );
        fixture.offsetX = 0.25f;
        fixture.offsetY = -0.5f;
        fixture.friction = 0.2f;
        fixtures.fixtures.add(fixture);

        if (owned) {
            SpatialBlocksComponent blocks =
                    world.getMapper(SpatialBlocksComponent.class).create(bodyEntityId);
            SpatialBlockData block = new SpatialBlockData();
            block.id = blockId;
            block.physicsCollision = true;
            blocks.blocks.add(block);
        }

        LayerService layerService = new LayerService(
                world,
                new TiledAllocatorService(),
                history.historyIds()
        );
        SelectionService selectionService = new SelectionService(world, layerService);
        EntityPropertiesContext context = new EntityPropertiesContext(
                world,
                history,
                physicsSelection,
                layerService,
                new AtlasStudioService(null),
                selectionService,
                new IconResolver(world),
                () -> { },
                1
        );

        physicsSelection.setSelectedFixture(bodyEntityId, fixture.fixtureId);
        FixturesPanel panel = new FixturesPanel(context);
        panel.setEntityId(bodyEntityId);
        return new FixturePanelFixture(
                world, history, physicsSelection, selectionService,
                bodyEntityId, fixtures, fixture.fixtureId, panel);
    }

    private static FixtureDefData fixture(int fixtureId, int shapeType) {
        FixtureDefData fixture = new FixtureDefData();
        fixture.fixtureId = fixtureId;
        fixture.shapeType = shapeType;
        fixture.halfW = 0.5f;
        fixture.halfH = 0.5f;
        fixture.radius = 0.5f;
        return fixture;
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private record FixturePanelFixture(
            World world,
            HistoryManager history,
            PhysicsSelectionService physicsSelection,
            SelectionService selectionService,
            int bodyEntityId,
            PhysicsFixturesComponent fixtures,
            int fixtureId,
            FixturesPanel panel
    ) {
        FixtureDefData fixture() {
            for (FixtureDefData fixture : fixtures.fixtures) {
                if (fixture != null && fixture.fixtureId == fixtureId) return fixture;
            }
            return null;
        }
    }
}
