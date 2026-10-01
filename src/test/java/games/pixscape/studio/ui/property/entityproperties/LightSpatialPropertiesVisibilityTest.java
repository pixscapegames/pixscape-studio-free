package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.IconResolver;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import games.pixscape.studio.service.asset.AnimationAssetAuthoringService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import games.pixscape.studio.ui.widget.FloatField;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LightSpatialPropertiesVisibilityTest {
    @BeforeClass
    public static void loadSkin() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadSkin() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void pointAndConeInspectorsShowSpatialSectionInSpatialLayer() throws Exception {
        World world = new World(new WorldConfiguration());
        IdentityRegistry identities = new IdentityRegistry();
        SceneMeta scene = new SceneMeta();
        scene.physicsEnabled = true;
        identities.bind(world, scene);
        ProjectConfig previousConfig = ProjectConfig.getInstance();
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Light Spatial UI Test");
        config.getCurrentSceneMeta().pixelsPerMeter = 100f;
        ProjectConfig.setInstance(config);
        GL20 originalGl = Gdx.gl;
        Gdx.gl = shaderCapableGl(originalGl);
        Gdx.gl20 = Gdx.gl;
        try {
            HistoryManager history = new HistoryManager(8);
            LayerService layers = new LayerService(world, null, history.historyIds(), identities);
            AssetMetaDatabase assets = new AssetMetaDatabase();
            EntityPropertiesContext context = new EntityPropertiesContext(
                    world, history, new PhysicsSelectionService(),
                    new PhysicsService(world, null, scene), layers,
                    new AtlasStudioService(null), new SelectionService(world, layers),
                    identities, new IconResolver(world), () -> {}, assets::findById,
                    ignored -> {}, () -> new Array<AnimationAssetMeta>(),
                    new AnimationAssetAuthoringService(() -> assets,
                            () -> new FileHandle(new File(System.getProperty("java.io.tmpdir"),
                                    "pixscape-light-spatial-ui-assets.json")), ignored -> {}),
                    0);

            int layerEntity = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(layerEntity);
            layer.layerIndex = 0;
            layer.spatialEnabled = true;
            world.getMapper(LayerMetaComponent.class).create(layerEntity);

            int point = world.create();
            world.getMapper(EntityIndexComponent.class).create(point).layerIndex = 0;
            world.getMapper(EntityMetaComponent.class).create(point).kind = EntityKind.POINT_LIGHT;
            world.getMapper(PointLightComponent.class).create(point).radius = 20f;
            DimensionsComponent pointDimensions = world.getMapper(DimensionsComponent.class).create(point);
            pointDimensions.width = pointDimensions.height = 40f;

            int cone = world.create();
            world.getMapper(EntityIndexComponent.class).create(cone).layerIndex = 0;
            world.getMapper(EntityMetaComponent.class).create(cone).kind = EntityKind.CONE_LIGHT;
            world.getMapper(ConeLightComponent.class).create(cone).radius = 30f;
            DimensionsComponent coneDimensions = world.getMapper(DimensionsComponent.class).create(cone);
            coneDimensions.width = coneDimensions.height = 60f;
            world.process();

            PointLightProperties pointPanel = new PointLightProperties(context);
            ConeLightProperties conePanel = new ConeLightProperties(context);
            pointPanel.setEntityId(point);
            conePanel.setEntityId(cone);
            ToggleSection pointSpatial = spatialSection(pointPanel);
            ToggleSection coneSpatial = spatialSection(conePanel);
            assertTrue(pointSpatial.isVisible());
            assertTrue(coneSpatial.isVisible());
            assertTrue(pointSpatial.getPrefHeight() > 0f);
            assertTrue(coneSpatial.getPrefHeight() > 0f);

            layer.spatialEnabled = false;
            pointPanel.setEntityId(point);
            conePanel.setEntityId(cone);
            assertFalse(pointSpatial.isVisible());
            assertFalse(coneSpatial.isVisible());

            world.getMapper(SpatialHeightComponent.class).create(point).height = 1f;
            pointPanel.setEntityId(point);
            assertTrue(pointSpatial.isVisible());

            technicalFootprint(world, point, 1, 0.05f);
            technicalFootprint(world, cone, 2, 0.05f);
            TransformComponent pointTransform = world.getMapper(TransformComponent.class).create(point);
            pointTransform.x = 12f;
            pointTransform.y = 34f;
            TransformComponent coneTransform = world.getMapper(TransformComponent.class).create(cone);
            coneTransform.x = 56f;
            coneTransform.y = 78f;
            pointPanel.setEntityId(point);
            conePanel.setEntityId(cone);
            FloatField pointRadius = radiusField(pointPanel);
            pointRadius.setText("80");
            pointRadius.commit();
            FloatField coneRadius = radiusField(conePanel);
            coneRadius.setText("90");
            coneRadius.commit();
            assertEquals(80f, world.getMapper(PointLightComponent.class).get(point).radius, 0f);
            assertEquals(90f, world.getMapper(ConeLightComponent.class).get(cone).radius, 0f);
            assertEquals(0.05f, world.getMapper(PhysicsShapesComponent.class)
                    .get(point).shapes.first().geometry.radius, 0f);
            assertEquals(0.05f, world.getMapper(PhysicsShapesComponent.class)
                    .get(cone).shapes.first().geometry.radius, 0f);
            assertEquals(12f, pointTransform.x, 0f);
            assertEquals(34f, pointTransform.y, 0f);
            assertEquals(56f, coneTransform.x, 0f);
            assertEquals(78f, coneTransform.y, 0f);
        } finally {
            ProjectConfig.setInstance(previousConfig);
            Gdx.gl = originalGl;
            Gdx.gl20 = originalGl;
            identities.bind(null, null);
            world.dispose();
        }
    }

    private static void technicalFootprint(World world, int entityId, int shapeId, float radiusMeters) {
        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).create(entityId);
        body.type = PhysicsBodyComponent.DYNAMIC;
        body.gravityScale = 0f;
        body.technicalSpatialLight = true;
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.physicsShapeId = shapeId;
        shape.geometry = new PhysicsGeometryData();
        shape.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        shape.geometry.radius = radiusMeters;
        shape.spatialFootprint = true;
        shape.technicalSpatialLight = true;
        shape.sensor = true;
        shape.maskBits = 0;
        shape.groupIndex = 0;
        world.getMapper(PhysicsShapesComponent.class).create(entityId).shapes.add(shape);
    }

    private static FloatField radiusField(Object panel) throws Exception {
        Field field = panel.getClass().getDeclaredField("radiusField");
        field.setAccessible(true);
        return (FloatField) field.get(panel);
    }

    private static GL20 shaderCapableGl(GL20 original) {
        return (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(),
                new Class<?>[]{GL20.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("glCreateShader") || name.equals("glCreateProgram")) return 1;
                    if (name.equals("glGetShaderiv")) {
                        ((IntBuffer) args[2]).put(0, 1);
                        return null;
                    }
                    if (name.equals("glGetProgramiv")) {
                        int parameter = (int) args[1];
                        ((IntBuffer) args[2]).put(0, parameter == GL20.GL_LINK_STATUS ? 1 : 0);
                        return null;
                    }
                    return method.invoke(original, args);
                });
    }

    private static ToggleSection spatialSection(Object panel) throws Exception {
        Field field = panel.getClass().getDeclaredField("spatialSection");
        field.setAccessible(true);
        return (ToggleSection) field.get(panel);
    }
}
