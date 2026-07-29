package games.pixscape.studio.ui.property.entityproperties.physics;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.kotcrab.vis.ui.widget.VisLabel;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.IconResolver;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.ui.property.entityproperties.EntityPropertiesContext;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class LinkedPhysicsUiContractTest {
    @BeforeClass
    public static void loadVisUiSkin() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadVisUiSkin() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void linkedFixtureHasPolygonPresentationWithoutAuthoredGeometry() {
        PhysicsShapeData linked = new PhysicsShapeData();
        linked.physicsShapeId = 4;
        linked.spatialBlockId = 7;

        Assert.assertTrue(FixturesPanel.isLinked(linked));
        Assert.assertEquals(
                PhysicsGeometryData.SHAPE_POLYGON,
                FixturesPanel.effectiveShapeType(linked));
        Assert.assertNull(linked.geometry);
    }

    @Test
    public void linkedSpatialBlockLabelTracksFixtureSelection() {
        try (Harness harness = new Harness()) {
            VisLabel label = harness.panel.findActor(
                    "physicsLinkedSpatialBlockLabel");
            Assert.assertNotNull(label);
            CollapsibleVisTable block = collapsibleAncestor(label);

            harness.select(1);
            Assert.assertTrue(label.isVisible());
            Assert.assertFalse(block.isCollapsed());
            Assert.assertEquals("Linked to Spatial Block #7",
                    label.getText().toString());

            harness.select(2);
            Assert.assertTrue(label.isVisible());
            Assert.assertEquals("Linked to Spatial Block #19",
                    label.getText().toString());

            harness.select(3);
            Assert.assertFalse(label.isVisible());
            Assert.assertTrue(block.isCollapsed());

            harness.selection.clearSelectionOnly();
            harness.panel.refreshNow();
            Assert.assertFalse(label.isVisible());
            Assert.assertTrue(block.isCollapsed());
        }
    }

    @Test
    public void panelsAndContextMenuKeepLinkedGeometryActionsDisabled()
            throws Exception {
        String fixtures = source(
                "src/main/java/games/pixscape/studio/ui/property/"
                        + "entityproperties/physics/FixturesPanel.java");
        String body = source(
                "src/main/java/games/pixscape/studio/ui/property/"
                        + "entityproperties/physics/BodyPanel.java");
        String context = source(
                "src/main/java/games/pixscape/studio/ui/contextmenu/"
                        + "StudioContextMenu.java");

        Assert.assertTrue(fixtures.contains("shapeBox.setDisabled(linked);"));
        Assert.assertTrue(fixtures.contains("offsetsBlock.show(false);"));
        Assert.assertTrue(fixtures.contains(
                "return fixture != null && !isLinked(fixture);"));
        Assert.assertTrue(fixtures.contains(
                "current == null || current.geometry == null"));
        Assert.assertTrue(fixtures.contains(
                "linkedSpatialBlockBlock.show(linked);"));
        Assert.assertFalse(fixtures.contains(
                "densityField.setDisabled(linked)"));
        Assert.assertFalse(fixtures.contains(
                "frictionField.setDisabled(linked)"));
        Assert.assertFalse(fixtures.contains(
                "restitutionField.setDisabled(linked)"));
        Assert.assertTrue(body.contains(
                "addPhysicsBox.setDisabled(hasLinkedShape(eid));"));
        Assert.assertTrue(body.contains(
                "bodyTypeBox.setDisabled(mTiled.has(eid));"));
        Assert.assertTrue(body.contains(
                "? PhysicsGeometryData.SHAPE_POLYGON"));
        Assert.assertTrue(context.contains(
                "selectedFixture.geometry != null"));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static CollapsibleVisTable collapsibleAncestor(Actor actor) {
        Actor current = actor;
        while (current != null && !(current instanceof CollapsibleVisTable)) {
            current = current.getParent();
        }
        Assert.assertNotNull(current);
        return (CollapsibleVisTable) current;
    }

    private static final class Harness implements AutoCloseable {
        final World world = new World(new WorldConfiguration());
        final HistoryManager history = new HistoryManager(8);
        final PhysicsSelectionService selection = new PhysicsSelectionService();
        final SceneMeta meta = new SceneMeta();
        final PhysicsService physics = new PhysicsService(world, null, meta);
        final IdentityRegistry identities = new IdentityRegistry();
        final FixturesPanel panel;
        final int body = world.create();

        Harness() {
            identities.bind(world, meta);
            LayerService layers = new LayerService(
                    world, null, history.historyIds(), identities);
            SelectionService entities = new SelectionService(world, layers);
            EntityPropertiesContext context = new EntityPropertiesContext(
                    world,
                    history,
                    selection,
                    physics,
                    layers,
                    new AtlasStudioService(null),
                    entities,
                    identities,
                    new IconResolver(world),
                    () -> {
                    },
                    0);
            PhysicsService.initDefaultBody(
                    world.getMapper(PhysicsBodyComponent.class).create(body));
            PhysicsShapesComponent shapes =
                    world.getMapper(PhysicsShapesComponent.class).create(body);
            shapes.shapes.add(linked(1, 7));
            shapes.shapes.add(linked(2, 19));
            shapes.shapes.add(PhysicsService.createDefaultShape(3));
            selection.focusBody(body);
            panel = new FixturesPanel(context);
            panel.setEntityId(body);
        }

        void select(int physicsShapeId) {
            selection.setSelectedShape(body, physicsShapeId);
            panel.refreshNow();
        }

        @Override
        public void close() {
            identities.bind(null, null);
            world.dispose();
        }

        private static PhysicsShapeData linked(
                int physicsShapeId, int spatialBlockId) {
            PhysicsShapeData shape = new PhysicsShapeData();
            shape.physicsShapeId = physicsShapeId;
            shape.spatialBlockId = spatialBlockId;
            return shape;
        }
    }
}
