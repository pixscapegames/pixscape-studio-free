package games.pixscape.studio.ui.main;

import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.service.Box2dWorldService;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WorldCanvasPhysicsPpmWiringTest {
    @Test
    public void applyingScenePpmDoesNotChangeAuthoredFootprint() {
        GdxNativesLoader.load();
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2());
        World world = new World(new com.artemis.WorldConfiguration());
        int entityId = world.create();
        SpatialFootprintComponent footprint = world.getMapper(SpatialFootprintComponent.class).create(entityId);
        footprint.radiusPx = 50f;
        WorldCanvas.applyPixelsPerMeter(box2d, 50f);
        world.process();

        Assert.assertEquals(50f, box2d.ppm, 0f);
        Assert.assertEquals(50f, footprint.radiusPx, 0f);
        world.dispose();
        box2d.dispose();
    }

    @Test
    public void livePpmRecompilesBeforeApplyAndUpdatesCameraAfterApply()
            throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/main/"
                        + "WorldCanvas.java"),
                StandardCharsets.UTF_8);
        String body = methodBody(source, "private void ensureBox2dFromMeta(");
        int rebuild = body.indexOf(
                "PhysicsService.rebuildPreparedBodyCaches(world(), ppm);");
        int applyAfterRebuild = body.indexOf(
                "applyPixelsPerMeter(", rebuild);
        int cameraAfterApply = body.indexOf(
                "box2DcameraUpdate();", applyAfterRebuild);
        int lastPpm = body.indexOf("lastPpm = ppm;", cameraAfterApply);

        Assert.assertTrue(rebuild >= 0);
        Assert.assertTrue(applyAfterRebuild > rebuild);
        Assert.assertTrue(cameraAfterApply > applyAfterRebuild);
        Assert.assertTrue(lastPpm > cameraAfterApply);

        String firstInit = body.substring(
                body.indexOf("if (firstInit)"),
                body.indexOf("else {", body.indexOf("if (firstInit)")));
        Assert.assertFalse(firstInit.contains(
                "rebuildPreparedBodyCaches"));
        Assert.assertTrue(firstInit.indexOf("applyPixelsPerMeter(")
                < firstInit.indexOf("box2DcameraUpdate();"));
        Assert.assertTrue(source.contains(
                "EventFlow.ScenePhysicsPixelsPerMeterChanged.class"));
    }

    private static String methodBody(String source, String signaturePrefix) {
        int signatureIndex = source.indexOf(signaturePrefix);
        if (signatureIndex < 0) {
            throw new AssertionError(
                    "Method signature not found: " + signaturePrefix);
        }
        int bodyStart = source.indexOf('{', signatureIndex);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) {
                return source.substring(bodyStart + 1, i);
            }
        }
        throw new AssertionError("Method body end not found.");
    }
}
