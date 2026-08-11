package games.pixscape.studio.ui.property;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class PropertiesPanelFixtureDeletionContractTest {

    @Test
    public void clearedDeletedFixtureFallsBackToFocusedBodyAndCannotRebindMissingFixture() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/property/PropertiesPanel.java"));

        Assert.assertTrue(source.contains(
                "EventFlow.i().subscribe(EventFlow.FixtureSelectionCleared.class"));
        Assert.assertTrue(source.contains("pendingView = PendingView.FIXTURE;"));
        Assert.assertTrue(source.contains("restoreAfterFixtureDeselection();"));
        Assert.assertTrue(source.contains("&& mPhysBody.has(bodyEntityId)"));
        Assert.assertTrue(source.contains("showBodyProperties(physicsContextBody);"));
        Assert.assertTrue(source.contains("&& fixtureExists(bodyEntityId, physicsShapeId)"));

        String pickingSource = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/system/PickingSystem.java"));
        Assert.assertTrue(pickingSource.contains(
                "EventFlow.i().subscribe(EventFlow.FixtureSelectionCleared.class"));
        Assert.assertTrue(pickingSource.contains("clearFixtureEditingState()"));
        Assert.assertTrue(pickingSource.contains("clearPolygonVertexMoveState();"));
        Assert.assertTrue(pickingSource.contains("physicsSelectionReconciler.reconcile();"));
        Assert.assertFalse(pickingSource.contains(
                "|| !mPhysBody.has(focusedBodyEid)"));
    }
}
