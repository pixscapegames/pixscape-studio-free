package games.pixscape.studio.ui.property;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LayerPropertiesCollisionVisibilityContractTest {

    @Test
    public void tiledMapPropertiesOwnsCollisions() throws Exception {
        String layer = read("src/main/java/games/pixscape/studio/ui/property/LayerProperties.java");
        String map = read("src/main/java/games/pixscape/studio/ui/property/TiledMapProperties.java");

        assertFalse(layer.contains("new VisCheckBox(\"Collisions\")"));
        assertTrue(map.contains("new VisCheckBox(\"Collisions\")"));
        assertTrue(map.contains("new AddPhysicsBodyCommand("));
        assertTrue(map.contains("new RemovePhysicsBodyCommand("));
        assertTrue(map.contains("mPhysicsBody.has(mapEntityId)"));
        assertTrue(map.contains("showRemoveCollisionsDialog(mapEntityId)"));
        assertTrue(map.contains("delete all collision shapes from this Map"));
        assertTrue(map.contains("dialog.button(\"Remove\", true)"));
        assertTrue(map.contains("dialog.button(\"Cancel\", false)"));
    }

    @Test
    public void ordinaryAndTiledSpatialPropertiesRemainDistinct() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/property/LayerProperties.java");
        String refresh = methodBody(source, "private void refreshFromModel(int layerEntityId)");

        assertTrue(refresh.contains("boolean isOrdinary = lic.type == LayerComponent.TYPE_CLASSIC"));
        assertTrue(refresh.contains("boolean ordinarySpatialVisible = isOrdinary && shouldShowOrdinarySpatialProperty("));
        assertTrue(refresh.contains("layerService.hasOtherSpatialActorLayer(layerEntityId)"));
        assertTrue(refresh.contains("boolean spatialSupported = ordinarySpatialVisible;"));
        assertTrue(refresh.contains("boolean spatialActive = lic.spatialEnabled;"));
        assertTrue(refresh.contains("spatialSection.show(spatialSupported);"));
        assertFalse(source.contains("new VisCheckBox(\"Spatial Depth\")"));
        assertTrue(source.contains("new VisCheckBox(\"Spatial\")"));
        assertTrue(source.contains("new ToggleSpatialActorLayerCommand("));
        assertFalse(source.contains("ToggleTiledMapSpatialDepthCommand"));
        assertFalse(source.contains("StudioEditingMode.SPATIAL"));
        assertFalse(source.contains("new VisLabel(\"Type:\")"));
        assertFalse(source.contains("typeDisplayName("));

        String map = read("src/main/java/games/pixscape/studio/ui/property/TiledMapProperties.java");
        assertTrue(map.contains("new VisCheckBox(\"Spatial Depth\")"));
        assertTrue(map.contains("new ToggleTiledMapSpatialDepthCommand("));
        assertTrue(map.contains("Default Altitude:"));
        assertTrue(map.contains("Default Height:"));
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
        assertTrue(source.contains("&& isEntityInSpatialEnabledLayer())"));
        assertFalse(source.contains("EventFlow.LayerSpatialDepthChanged.class"));
        assertTrue(source.contains("EventFlow.SpatialHeightChanged.class"));
    }

    @Test
    public void addingMapCollisionsCannotEnableScenePhysics() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/property/TiledMapProperties.java");

        assertTrue(source.contains("if (requested && !isScenePhysicsEnabled())"));
        assertFalse(source.contains("physicsEnabled = true"));
    }

    @Test
    public void propertiesPanelRoutesActualMapSelectionAndTargetEvent() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/property/PropertiesPanel.java");
        String selection = methodBody(source, "public void onSelectionChanged(IntArray selectionSnapshot)");

        assertTrue(selection.contains("if (mTiled.has(e))"));
        assertTrue(selection.contains("showTiledMapProperties(e)"));
        assertTrue(source.contains("EventFlow.TiledMapEditingTargetChanged.class"));
        assertFalse(source.contains("findTiledMapForHost("));
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
