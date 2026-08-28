package games.pixscape.studio.ui.property;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LayerPropertiesCollisionVisibilityContractTest {

    @Test
    public void collisionsSectionRequiresTiledLayerAndScenePhysics() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/property/LayerProperties.java");
        String refresh = methodBody(source, "private void refreshFromModel(int layerEntityId)");

        assertTrue(refresh.contains("boolean scenePhysicsEnabled = isScenePhysicsEnabled();"));
        assertTrue(refresh.contains("boolean collisionsSupported = isTiled && scenePhysicsEnabled;"));
        assertTrue(refresh.contains("collisionsSection.show(collisionsSupported);"));
    }

    @Test
    public void ordinaryAndTiledSpatialPropertiesRemainDistinct() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/property/LayerProperties.java");
        String refresh = methodBody(source, "private void refreshFromModel(int layerEntityId)");

        assertTrue(refresh.contains("boolean isOrdinary = lic.type == LayerComponent.TYPE_CLASSIC"));
        assertTrue(refresh.contains("boolean ordinarySpatialVisible = isOrdinary && shouldShowOrdinarySpatialProperty("));
        assertTrue(refresh.contains("layerService.hasOtherSpatialActorLayer(layerEntityId)"));
        assertTrue(refresh.contains("boolean spatialSupported = isTiled || ordinarySpatialVisible;"));
        assertTrue(refresh.contains("boolean spatialActive = isLayerSpatialEnabled(layerEntityId);"));
        assertTrue(refresh.contains("spatialSection.show(spatialSupported);"));
        assertTrue(refresh.contains("spatialBlock.show(isTiled && spatialActive);"));
        assertTrue(source.contains("Default Altitude:"));
        assertTrue(source.contains("Default Height:"));
        assertTrue(source.contains("new VisCheckBox(\"Spatial Depth\")"));
        assertTrue(source.contains("spatialCheckBox.setText(isTiled ? \"Spatial Depth\" : \"Spatial\")"));
        assertTrue(source.contains("new ToggleSpatialActorLayerCommand("));
        assertTrue(source.contains("new ToggleLayerSpatialDepthCommand("));
        assertFalse(source.contains("StudioEditingMode.SPATIAL"));
    }

    @Test
    public void ordinarySpatialVisibilityHonorsEligibilityUniquenessAndAuthoredState() {
        assertTrue(LayerProperties.shouldShowOrdinarySpatialProperty(false, true, false));
        assertFalse(LayerProperties.shouldShowOrdinarySpatialProperty(false, true, true));
        assertFalse(LayerProperties.shouldShowOrdinarySpatialProperty(false, false, false));
        assertTrue(LayerProperties.shouldShowOrdinarySpatialProperty(true, false, true));
    }

    @Test
    public void scenePropertiesDoesNotExposeLayerSpatialControls() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/property/SceneProperties.java");

        assertFalse(source.contains("2.5D Spatial Depth System"));
    }

    @Test
    public void entityPropertiesExposeSimplifiedSpatialSection() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/property/entityproperties/EntityProperties.java");

        assertFalse(source.contains("SpatialHeightPanel"));
        assertTrue(source.contains("SpatialPhysicsPanel"));
        assertTrue(source.contains("new ToggleSection(\"Spatial\""));
        assertTrue(source.contains("boolean isSprite = kind == EntityKind.SPRITE;"));
        assertTrue(source.contains("spatialSection.setApplicable(isSpatialApplicable(isSprite, isAnim));"));
        assertTrue(source.contains("&& isEntityInSpatialLayer()) || hasSpatialActorState();"));
        assertFalse(source.contains("EventFlow.LayerSpatialDepthChanged.class"));
        assertTrue(source.contains("EventFlow.SpatialHeightChanged.class"));
    }

    @Test
    public void addingCollisionsCannotAddLayerPhysicsWhenScenePhysicsIsOff() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/property/LayerProperties.java");
        String addPhysics = methodBody(source, "private void addPhysicsToTiledLayer(int layerEntityId)");

        assertTrue(source.contains("if (!isScenePhysicsEnabled()) {\n" +
                "                    refreshFromModel(layerEntityId);"));
        assertTrue(addPhysics.contains("if (!isScenePhysicsEnabled())"));
        assertTrue(addPhysics.contains("return;"));
    }

    @Test
    public void localCollisionRemovalExplainsThatItCanBeUndone() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/property/LayerProperties.java");

        assertTrue(source.contains("Removing collisions will delete the physics on this layer.\n" +
                "                        This action can be undone."));
        assertFalse(source.contains("Removing collisions will permanently delete"));
    }

    @Test
    public void propertiesPanelRefreshesBoundLayerWhenScenePhysicsChanges() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/property/PropertiesPanel.java");

        assertTrue(source.contains("EventFlow.i().subscribe(EventFlow.ScenePhysicsEnabledChanged.class"));
        assertTrue(source.contains("pendingLayer = boundLayer;"));
        assertTrue(source.contains("pendingView = PendingView.LAYER;"));
        assertTrue(source.contains("EventFlow.i().subscribe(EventFlow.CurrentCameraChanged.class"));

        String onActiveLayerChanged = methodBody(source, "public void onActiveLayerChanged(int newLayerEntityId)");
        assertTrue(onActiveLayerChanged.contains("layerProperties.setLayerEntityId(newLayerEntityId);"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String methodBody(String source, String signaturePrefix) {
        int signatureIndex = source.indexOf(signaturePrefix);
        if (signatureIndex < 0) throw new AssertionError("Method signature not found: " + signaturePrefix);

        int bodyStart = source.indexOf('{', signatureIndex);
        if (bodyStart < 0) throw new AssertionError("Method body start not found: " + signaturePrefix);

        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart + 1, i);
                }
            }
        }
        throw new AssertionError("Method body end not found: " + signaturePrefix);
    }
}
