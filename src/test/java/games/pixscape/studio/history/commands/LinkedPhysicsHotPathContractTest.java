package games.pixscape.studio.history.commands;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class LinkedPhysicsHotPathContractTest {
    @Test
    public void spatialDragAndFrameSystemsDoNotCompilePhysics() throws Exception {
        assertNoCompilation(
                "src/main/java/games/pixscape/studio/service/spatial/"
                        + "SpatialWallEditSession.java");
        assertNoCompilation(
                "src/main/java/games/pixscape/studio/system/"
                        + "PickingSystem.java");
        assertNoCompilation(
                "src/main/java/games/pixscape/studio/system/"
                        + "GizmoSystem.java");
    }

    private static void assertNoCompilation(String path) throws Exception {
        String source = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        Assert.assertFalse(path, source.contains(
                "PhysicsService.prepareBodyCandidate"));
        Assert.assertFalse(path, source.contains(
                "PhysicsService.rebuildPreparedBodyCaches"));
    }
}
