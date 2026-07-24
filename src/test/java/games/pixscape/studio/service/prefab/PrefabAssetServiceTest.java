package games.pixscape.studio.service.prefab;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.service.entitygraph.EntityGraph;
import games.pixscape.studio.service.entitygraph.EntityGraphCaptureService;
import games.pixscape.studio.service.entitygraph.EntityGraphInstantiationResult;
import games.pixscape.studio.service.entitygraph.EntityGraphInstantiationService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

public class PrefabAssetServiceTest {
    @Before
    public void activateSceneAllocator() {
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Main");
        ProjectConfig.setInstance(config);
    }

    @Test
    public void saveLoad_emptyOrSimpleGraph() {
        World world = new World(new WorldConfiguration());
        int entity = body(world);
        TransformComponent t = world.getMapper(TransformComponent.class).get(entity);
        t.x = 11f;
        t.y = -7f;
        t.rotationRad = 0.25f;

        EntityGraph graph = new EntityGraphCaptureService(world).capture(arr(entity));

        FileHandle file = tmpFile("simple.pixprefab");
        PrefabAssetService service = new PrefabAssetService(world);
        service.savePrefab(file, "simple", graph);
        EntityGraph loaded = service.loadPrefab(file);

        String serialized = file.readString("UTF-8");
        assertPrefabHeader(serialized, "simple");
        Assert.assertTrue(serialized.contains("\"entities\""));
        Assert.assertFalse(serialized.contains("ownerClass"));
        Assert.assertFalse(serialized.contains("fieldName"));
        Assert.assertFalse(serialized.contains("fieldType"));
        Assert.assertFalse(serialized.contains("valueJson"));

        Assert.assertEquals(graph.size(), loaded.size());

        GenericEntityInitializer loadedInit = loaded.entries().get(0).initializer();
        int probe = world.create();
        loadedInit.init(probe);

        TransformComponent restored = world.getMapper(TransformComponent.class).get(probe);
        Assert.assertEquals(11f, restored.x, 0.0001f);
        Assert.assertEquals(-7f, restored.y, 0.0001f);
        Assert.assertEquals(0.25f, restored.rotationRad, 0.0001f);
        PixscapeIdentityComponent restoredIdentity = world.getMapper(PixscapeIdentityComponent.class).get(probe);
        Assert.assertNotNull(restoredIdentity);
        Assert.assertEquals("Body", restoredIdentity.name);

        PhysicsShapesComponent restoredFixtures = world.getMapper(PhysicsShapesComponent.class).get(probe);
        Assert.assertNotNull(restoredFixtures);
        Assert.assertTrue(restoredFixtures.shapes.size > 0);

        HistoryManager hm = new HistoryManager(32);
        IdentityRegistry reg = new IdentityRegistry();
        reg.bind(world, new games.pixscape.studio.configuration.SceneMeta());
        reg.rebuild();

        EntityGraphInstantiationResult result = new EntityGraphInstantiationService(
                world, hm, reg, new games.pixscape.runtime.service.PhysicsService(
                world, null, new games.pixscape.studio.configuration.SceneMeta()))
                .instantiate(loaded, 0, 0f, 0f, "Instantiate Prefab");

        Assert.assertTrue(result.createdIds().size > 0);
    }

    @Test
    public void saveLoad_visibleSpritePrefabRestoresVisualComponents() {
        World world = new World(new WorldConfiguration());
        int entity = sprite(world);
        EntityGraph graph = new EntityGraphCaptureService(world).capture(arr(entity));

        FileHandle file = tmpFile("sprite.pixprefab");
        PrefabAssetService service = new PrefabAssetService(world);
        service.savePrefab(file, "sprite", graph);
        EntityGraph loaded = service.loadPrefab(file);

        GenericEntityInitializer loadedInit = loaded.entries().get(0).initializer();
        int probe = world.create();
        loadedInit.init(probe);

        Assert.assertNotNull(world.getMapper(TransformComponent.class).get(probe));
        Assert.assertNotNull(world.getMapper(DimensionsComponent.class).get(probe));
        Assert.assertNotNull(world.getMapper(AssetRefComponent.class).get(probe));
        Assert.assertNotNull(world.getMapper(TextureRegionComponent.class).get(probe));
        Assert.assertNotNull(world.getMapper(RenderMaterialComponent.class).get(probe));
        Assert.assertNotNull(world.getMapper(EntityIndexComponent.class).get(probe));
        Assert.assertNotNull(world.getMapper(TintComponent.class).get(probe));
        Assert.assertNotNull(world.getMapper(PixscapeIdentityComponent.class).get(probe));
        Assert.assertNotNull(world.getMapper(EntityMetaComponent.class).get(probe));

        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).get(probe);
        Assert.assertEquals("Sprite", identity.name);

        HistoryManager hm = new HistoryManager(32);
        IdentityRegistry reg = new IdentityRegistry();
        reg.bind(world, new games.pixscape.studio.configuration.SceneMeta());
        reg.rebuild();

        EntityGraphInstantiationResult result = new EntityGraphInstantiationService(
                world, hm, reg, new games.pixscape.runtime.service.PhysicsService(
                world, null, new games.pixscape.studio.configuration.SceneMeta()))
                .instantiate(loaded, 0, 0f, 0f, "Instantiate Prefab");
        Assert.assertTrue(result.createdIds().size > 0);

        int createdEntity = result.createdIds().get(0);
        Assert.assertNotNull(world.getMapper(TransformComponent.class).get(createdEntity));
        Assert.assertNotNull(world.getMapper(EntityIndexComponent.class).get(createdEntity));
        Assert.assertNotNull(world.getMapper(DimensionsComponent.class).get(createdEntity));
        Assert.assertNotNull(world.getMapper(AssetRefComponent.class).get(createdEntity));
        Assert.assertNotNull(world.getMapper(TextureRegionComponent.class).get(createdEntity));
        Assert.assertNotNull(world.getMapper(RenderMaterialComponent.class).get(createdEntity));
        Assert.assertNotNull(world.getMapper(TintComponent.class).get(createdEntity));
        Assert.assertNotNull(world.getMapper(PixscapeIdentityComponent.class).get(createdEntity));
        Assert.assertNotNull(world.getMapper(EntityMetaComponent.class).get(createdEntity));
    }

    @Test
    public void saveLoad_remapsBodyJointReferencesAfterInstantiate() {
        World world = new World(new WorldConfiguration());
        int a = body(world);
        int b = body(world);
        int j = distanceJoint(world, a, b);

        EntityGraph graph = new EntityGraphCaptureService(world).capture(arr(a, b));

        FileHandle file = tmpFile("joint.pixprefab");
        PrefabAssetService service = new PrefabAssetService(world);
        service.savePrefab(file, "joint", graph);
        EntityGraph loaded = service.loadPrefab(file);

        String serialized = file.readString("UTF-8");
        assertPrefabHeader(serialized, "joint");
        Assert.assertTrue(serialized.contains("\"entities\""));
        Assert.assertFalse(serialized.contains("ownerClass"));
        Assert.assertFalse(serialized.contains("fieldName"));
        Assert.assertFalse(serialized.contains("fieldType"));
        Assert.assertFalse(serialized.contains("valueJson"));

        HistoryManager hm = new HistoryManager(32);
        IdentityRegistry reg = new IdentityRegistry();
        reg.bind(world, new games.pixscape.studio.configuration.SceneMeta());
        reg.rebuild();

        EntityGraphInstantiationResult result = new EntityGraphInstantiationService(
                world, hm, reg, new games.pixscape.runtime.service.PhysicsService(
                world, null, new games.pixscape.studio.configuration.SceneMeta()))
                .instantiate(loaded, 0, 0f, 0f, "Instantiate Prefab");

        int pastedJ = result.sourceToCreated().get(j, -1);
        Assert.assertTrue("Pasted joint should exist in sourceToCreated map", pastedJ >= 0);

        PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).get(pastedJ);
        Assert.assertNotNull("Pasted joint should have PhysicsJointComponent", joint);

        Assert.assertEquals(result.sourceToCreated().get(a, -1), joint.aEid);
        Assert.assertEquals(result.sourceToCreated().get(b, -1), joint.bEid);
    }


    @Test
    public void saveLoad_remapsWheelJointReferencesAfterInstantiate() {
        World world = new World(new WorldConfiguration());
        int a = body(world);
        int b = body(world);
        int j = wheelJoint(world, a, b);
        PhysicsWheelJointComponent sourceWheel = world.getMapper(PhysicsWheelJointComponent.class).get(j);
        sourceWheel.axisX = 1f;
        sourceWheel.axisY = 0f;
        sourceWheel.frequencyHz = 2f;
        sourceWheel.dampingRatio = 0.2f;
        sourceWheel.enableMotor = true;
        sourceWheel.motorSpeedRad = 3f;
        sourceWheel.maxMotorTorque = 4f;

        EntityGraph graph = new EntityGraphCaptureService(world).capture(arr(a, b));

        FileHandle file = tmpFile("wheel.pixprefab");
        PrefabAssetService service = new PrefabAssetService(world);
        service.savePrefab(file, "wheel", graph);
        EntityGraph loaded = service.loadPrefab(file);

        String serialized = file.readString("UTF-8");
        assertPrefabHeader(serialized, "wheel");
        Assert.assertTrue("Serialized prefab should contain wheelJoint block", serialized.contains("\"wheelJoint\""));

        HistoryManager hm = new HistoryManager(32);
        IdentityRegistry reg = new IdentityRegistry();
        reg.bind(world, new games.pixscape.studio.configuration.SceneMeta());
        reg.rebuild();

        EntityGraphInstantiationResult result = new EntityGraphInstantiationService(
                world, hm, reg, new games.pixscape.runtime.service.PhysicsService(
                world, null, new games.pixscape.studio.configuration.SceneMeta()))
                .instantiate(loaded, 0, 0f, 0f, "Instantiate Prefab");

        int pastedJ = result.sourceToCreated().get(j, -1);
        Assert.assertTrue("Pasted wheel joint should exist in sourceToCreated map", pastedJ >= 0);

        PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).get(pastedJ);
        Assert.assertNotNull("Pasted wheel joint should have PhysicsJointComponent", joint);
        Assert.assertEquals(result.sourceToCreated().get(a, -1), joint.aEid);
        Assert.assertEquals(result.sourceToCreated().get(b, -1), joint.bEid);

        PhysicsWheelJointComponent wheel = world.getMapper(PhysicsWheelJointComponent.class).get(pastedJ);
        Assert.assertNotNull("Pasted wheel joint should have PhysicsWheelJointComponent", wheel);
        Assert.assertEquals(1f, wheel.axisX, 0f);
        Assert.assertEquals(0f, wheel.axisY, 0f);
        Assert.assertEquals(2f, wheel.frequencyHz, 0f);
        Assert.assertEquals(0.2f, wheel.dampingRatio, 0f);
        Assert.assertTrue(wheel.enableMotor);
        Assert.assertEquals(3f, wheel.motorSpeedRad, 0f);
        Assert.assertEquals(4f, wheel.maxMotorTorque, 0f);
    }

    @Test
    public void saveLoad_remapsGearJointReferencesAfterInstantiate() {
        World world = new World(new WorldConfiguration());
        int a = body(world);
        int b = body(world);
        int c = body(world);

        int j1 = revoluteJoint(world, a, b);
        int j2 = prismaticJoint(world, b, c);
        int g = gearJoint(world, a, c, j1, j2);

        EntityGraph graph = new EntityGraphCaptureService(world).capture(arr(a, b, c));

        FileHandle file = tmpFile("gear.pixprefab");
        PrefabAssetService service = new PrefabAssetService(world);
        service.savePrefab(file, "gear", graph);
        EntityGraph loaded = service.loadPrefab(file);

        String serialized = file.readString("UTF-8");
        assertPrefabHeader(serialized, "gear");
        Assert.assertTrue(serialized.contains("\"entities\""));
        Assert.assertFalse(serialized.contains("ownerClass"));
        Assert.assertFalse(serialized.contains("fieldName"));
        Assert.assertFalse(serialized.contains("fieldType"));
        Assert.assertFalse(serialized.contains("valueJson"));

        HistoryManager hm = new HistoryManager(32);
        IdentityRegistry reg = new IdentityRegistry();
        reg.bind(world, new games.pixscape.studio.configuration.SceneMeta());
        reg.rebuild();

        EntityGraphInstantiationResult result = new EntityGraphInstantiationService(
                world, hm, reg, new games.pixscape.runtime.service.PhysicsService(
                world, null, new games.pixscape.studio.configuration.SceneMeta()))
                .instantiate(loaded, 0, 0f, 0f, "Instantiate Prefab");

        int pastedG = result.sourceToCreated().get(g, -1);
        Assert.assertTrue("Pasted gear joint should exist in sourceToCreated map", pastedG >= 0);

        PhysicsGearJointComponent gear = world.getMapper(PhysicsGearJointComponent.class).get(pastedG);
        Assert.assertNotNull("Pasted gear joint should have PhysicsGearJointComponent", gear);

        Assert.assertEquals(result.sourceToCreated().get(j1, -1), gear.joint1Eid);
        Assert.assertEquals(result.sourceToCreated().get(j2, -1), gear.joint2Eid);
    }

    @Test
    public void saveLoad_preservesPolygonSourceAndAllocatesFreshIdentity() {
        World world = new World(new WorldConfiguration());
        int e = body(world);

        PhysicsShapesComponent sources =
                world.getMapper(PhysicsShapesComponent.class).get(e);
        sources.shapes.clear();
        PhysicsShapeData polygon = new PhysicsShapeData();
        polygon.physicsShapeId = 77;
        polygon.shapeType = PhysicsShapeData.SHAPE_POLYGON;
        polygon.polygonVertexCount = 5;
        polygon.polygonVertices = new float[]{0f, 0f, 2f, 0f, 3f, 1f, 1f, 3f, -1f, 1f};
        sources.shapes.add(polygon);

        EntityGraph graph = new EntityGraphCaptureService(world).capture(arr(e));
        FileHandle file = tmpFile("authoring.pixprefab");
        PrefabAssetService service = new PrefabAssetService(world);
        service.savePrefab(file, "authoring", graph);
        EntityGraph loaded = service.loadPrefab(file);

        String serialized = file.readString("UTF-8");
        Assert.assertTrue(serialized.contains("\"physicsShapes\""));
        Assert.assertFalse(serialized.contains("PhysicsCompiledFixturesComponent"));

        HistoryManager hm = new HistoryManager(32);
        IdentityRegistry reg = new IdentityRegistry();
        reg.bind(world, new games.pixscape.studio.configuration.SceneMeta());
        reg.rebuild();
        EntityGraphInstantiationResult result = new EntityGraphInstantiationService(
                world, hm, reg, new games.pixscape.runtime.service.PhysicsService(
                world, null, new games.pixscape.studio.configuration.SceneMeta()))
                .instantiate(loaded, 0, 0f, 0f, "Instantiate Prefab");

        int created = result.createdIds().get(0);
        PhysicsShapesComponent restored =
                world.getMapper(PhysicsShapesComponent.class).get(created);
        Assert.assertNotNull(restored);
        Assert.assertEquals(1, restored.shapes.size);
        PhysicsShapeData restoredPolygon = restored.shapes.first();
        Assert.assertEquals(5, restoredPolygon.polygonVertexCount);
        Assert.assertArrayEquals(polygon.polygonVertices, restoredPolygon.polygonVertices, 0f);
        Assert.assertNotEquals(polygon.physicsShapeId, restoredPolygon.physicsShapeId);
        Assert.assertTrue(restoredPolygon.physicsShapeId > 0);
        Assert.assertNotSame(polygon, restoredPolygon);
        Assert.assertNotSame(polygon.polygonVertices, restoredPolygon.polygonVertices);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidPrefabTypeThrows() {
        World world = new World(new WorldConfiguration());
        FileHandle file = tmpFile("bad-type.pixprefab");
        file.writeString(
                "{\"type\":\"wrong\",\"version\":1,\"entities\":[]}",
                false,
                "UTF-8"
        );

        new PrefabAssetService(world).loadPrefab(file);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsupportedVersionThrows() {
        World world = new World(new WorldConfiguration());
        FileHandle file = tmpFile("bad-version.pixprefab");
        file.writeString(
                "{\"type\":\"pixscape-prefab\",\"version\":2,\"entities\":[]}",
                false,
                "UTF-8"
        );

        new PrefabAssetService(world).loadPrefab(file);
    }

    private static void assertPrefabHeader(String serialized, String expectedName) {
        Assert.assertTrue("Prefab JSON should contain type field", serialized.contains("\"type\""));
        Assert.assertTrue("Prefab JSON should contain pixscape-prefab type", serialized.contains("\"pixscape-prefab\""));
        Assert.assertTrue("Prefab JSON should contain version field", serialized.contains("\"version\""));
        Assert.assertTrue("Prefab JSON should contain name field", serialized.contains("\"name\""));
        Assert.assertTrue("Prefab JSON should contain prefab name", serialized.contains("\"" + expectedName + "\""));
    }

    private static FileHandle tmpFile(String name) {
        File dir = new File(System.getProperty("java.io.tmpdir"), "pixscape-prefab-tests");
        dir.mkdirs();
        return new FileHandle(new File(dir, name));
    }

    private static IntArray arr(int... ids) {
        IntArray a = new IntArray();
        for (int id : ids) {
            a.add(id);
        }
        return a;
    }

    private static int body(World w) {
        int e = w.create();

        w.getMapper(TransformComponent.class).create(e);
        w.getMapper(EntityIndexComponent.class).create(e);
        PixscapeIdentityComponent identity = w.getMapper(PixscapeIdentityComponent.class).create(e);
        identity.name = "Body";
        w.getMapper(PhysicsBodyComponent.class).create(e);

        PhysicsShapesComponent f = w.getMapper(PhysicsShapesComponent.class).create(e);
        PhysicsShapeData d = new PhysicsShapeData();
        d.shapeType = PhysicsShapeData.SHAPE_BOX;
        f.shapes.add(d);

        return e;
    }

    private static int sprite(World w) {
        int e = w.create();
        w.getMapper(TransformComponent.class).create(e);
        w.getMapper(EntityIndexComponent.class).create(e);
        PixscapeIdentityComponent identity = w.getMapper(PixscapeIdentityComponent.class).create(e);
        identity.name = "Sprite";
        EntityMetaComponent meta = w.getMapper(EntityMetaComponent.class).create(e);
        meta.kind = games.pixscape.studio.model.EntityKind.SPRITE;
        DimensionsComponent d = w.getMapper(DimensionsComponent.class).create(e);
        d.width = 32f;
        d.height = 24f;
        TextureRegionComponent tr = w.getMapper(TextureRegionComponent.class).create(e);
        tr.u1 = 0f; tr.v1 = 0f; tr.u2 = 1f; tr.v2 = 1f;
        tr.pixW = 32; tr.pixH = 24; tr.valid = true;
        RenderMaterialComponent mat = w.getMapper(RenderMaterialComponent.class).create(e);
        mat.shaderIdx = 1;
        mat.blendModeId = 0;
        mat.textureHandle = 42;
        mat.debugAtlasTag = "main";
        AssetRefComponent src = w.getMapper(AssetRefComponent.class).create(e);
        src.assetId = 100;
        src.atlasTag = "main";
        TintComponent tint = w.getMapper(TintComponent.class).create(e);
        tint.rgba = 0xFFEEDDCC;
        return e;
    }

    private static int distanceJoint(World w, int a, int b) {
        int e = base(w, PhysicsJointComponent.TYPE_DISTANCE, a, b);
        w.getMapper(PhysicsDistanceJointComponent.class).create(e);
        return e;
    }

    private static int revoluteJoint(World w, int a, int b) {
        int e = base(w, PhysicsJointComponent.TYPE_REVOLUTE, a, b);
        w.getMapper(PhysicsRevoluteJointComponent.class).create(e);
        return e;
    }

    private static int prismaticJoint(World w, int a, int b) {
        int e = base(w, PhysicsJointComponent.TYPE_PRISMATIC, a, b);
        w.getMapper(PhysicsPrismaticJointComponent.class).create(e);
        return e;
    }


    private static int wheelJoint(World w, int a, int b) {
        int e = base(w, PhysicsJointComponent.TYPE_WHEEL, a, b);
        PhysicsWheelJointComponent wheel = w.getMapper(PhysicsWheelJointComponent.class).create(e);
        wheel.axisX = 0f;
        wheel.axisY = 1f;
        wheel.enableMotor = true;
        wheel.motorSpeedRad = 2.5f;
        wheel.maxMotorTorque = 5f;
        wheel.frequencyHz = 4f;
        wheel.dampingRatio = 0.7f;
        return e;
    }

    private static int gearJoint(World w, int a, int b, int j1, int j2) {
        int e = base(w, PhysicsJointComponent.TYPE_GEAR, a, b);

        PhysicsGearJointComponent g = w.getMapper(PhysicsGearJointComponent.class).create(e);
        g.joint1Eid = j1;
        g.joint2Eid = j2;

        return e;
    }

    private static int base(World w, int type, int a, int b) {
        int e = w.create();

        PhysicsJointComponent j = w.getMapper(PhysicsJointComponent.class).create(e);
        j.type = type;
        j.aEid = a;
        j.bEid = b;

        return e;
    }
}
