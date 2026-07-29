package games.pixscape.studio.ui.property.entityproperties.physics;

import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class LinkedPhysicsUiContractTest {
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
}
