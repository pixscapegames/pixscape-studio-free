package games.pixscape.studio.service.prefab;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.component.PrefabInstanceComponent;
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
        Assert.assertEquals(
                3, new JsonReader().parse(serialized).getInt("version"));
        Assert.assertTrue(serialized.contains("\"entities\""));
        Assert.assertTrue(new JsonReader().parse(serialized)
                .get("entities").get(0).get("quadDeform").isNull());
        Assert.assertFalse(serialized.contains("ownerClass"));
        Assert.assertFalse(serialized.contains("fieldName"));
        Assert.assertFalse(serialized.contains("fieldType"));
        Assert.assertFalse(serialized.contains("valueJson"));
        Assert.assertFalse(serialized.contains("EntityEditMode"));
        Assert.assertFalse(serialized.contains("quadEditEntityId"));
        Assert.assertFalse(serialized.contains("hoveredQuadVertexIndex"));
        Assert.assertFalse(serialized.contains("movingQuadVertex"));
        Assert.assertFalse(serialized.contains("displayOffset"));
        Assert.assertFalse(serialized.contains("parallaxOffset"));
        FileHandle fragmentFile =
                file.sibling(file.nameWithoutExtension() + ".pixfragment.json");
        Assert.assertEquals(
                2,
                new JsonReader().parse(fragmentFile).getInt("schemaVersion"));

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
        Assert.assertFalse(world.getMapper(QuadDeformComponent.class).has(probe));

        HistoryManager hm = new HistoryManager(32);
        IdentityRegistry reg = new IdentityRegistry();
        reg.bind(world, new games.pixscape.studio.configuration.SceneMeta());
        reg.rebuild();

        EntityGraphInstantiationResult result = new EntityGraphInstantiationService(
                world, hm, reg, new games.pixscape.runtime.service.PhysicsService(
                world, null, new games.pixscape.studio.configuration.SceneMeta()))
                .instantiate(loaded, 0, 0f, 0f, "Instantiate Prefab");

        Assert.assertTrue(result.createdIds().size > 0);
        Assert.assertFalse(world.getMapper(QuadDeformComponent.class)
                .has(result.createdIds().first()));
    }

    @Test
    public void savingGroupedSceneEntitiesFlattensMembershipAndLaterDropGetsOnlyFreshMetadata() {
        World world = new World(new WorldConfiguration());
        int entity = body(world);
        PrefabInstanceComponent original =
                world.getMapper(PrefabInstanceComponent.class).create(entity);
        original.instanceId = 12;
        original.prefabId = "Castle";

        FileHandle file = tmpFile("fortress.pixprefab");
        PrefabAssetService assets = new PrefabAssetService(world);
        assets.savePrefab(
                file, "fortress", new EntityGraphCaptureService(world).capture(arr(entity)));
        Assert.assertFalse(file.readString("UTF-8").contains("PrefabInstance"));
        Assert.assertFalse(file.sibling("fortress.pixfragment.json")
                .readString("UTF-8").contains("PrefabInstance"));

        EntityGraph loaded = assets.loadPrefab(file);
        int probe = world.create();
        loaded.entries().get(0).initializer().init(probe);
        Assert.assertFalse(world.getMapper(PrefabInstanceComponent.class).has(probe));

        HistoryManager history = new HistoryManager(8);
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, new games.pixscape.studio.configuration.SceneMeta());
        identities.rebuild();
        EntityGraphInstantiationResult dropped = new EntityGraphInstantiationService(
                world, history, identities, new games.pixscape.runtime.service.PhysicsService(
                world, null, new games.pixscape.studio.configuration.SceneMeta()))
                .instantiatePrefab(loaded, 0, 0f, 0f, "Drop Fortress", 13, "Fortress");
        PrefabInstanceComponent fresh = world.getMapper(PrefabInstanceComponent.class)
                .get(dropped.createdIds().first());
        Assert.assertEquals(13, fresh.instanceId);
        Assert.assertEquals("Fortress", fresh.prefabId);
        Assert.assertEquals(12, original.instanceId);
    }

    @Test
    public void saveLoadInstantiate_preservesQuadDeformationInPrefabAndRuntimeFragment() {
        World world = new World(new WorldConfiguration());
        int entity = sprite(world);
        QuadDeformComponent source = world.getMapper(QuadDeformComponent.class).create(entity);
        setQuad(source, 1.25f, -2.5f, 3.75f, -4.5f, 5.25f, -6.5f, 7.75f, -8.5f);

        EntityGraph graph = new EntityGraphCaptureService(world).capture(arr(entity));
        FileHandle file = tmpFile("quad-deform.pixprefab");
        PrefabAssetService service = new PrefabAssetService(world);
        service.savePrefab(file, "quad-deform", graph);

        JsonValue prefabRoot = new JsonReader().parse(file.readString("UTF-8"));
        Assert.assertEquals(3, prefabRoot.getInt("version"));
        JsonValue prefabQuad = prefabRoot.get("entities").get(0).get("quadDeform");
        Assert.assertNotNull(prefabQuad);
        assertQuadJson(prefabQuad);

        FileHandle fragmentFile =
                file.sibling(file.nameWithoutExtension() + ".pixfragment.json");
        JsonValue fragmentRoot = new JsonReader().parse(fragmentFile.readString("UTF-8"));
        JsonValue fragmentQuad = findNamed(fragmentRoot, "QuadDeformComponent");
        Assert.assertNotNull("Runtime fragment should contain structured QuadDeformComponent data", fragmentQuad);
        assertQuadJson(fragmentQuad);

        EntityGraph loaded = service.loadPrefab(file);
        int probe = world.create();
        loaded.entries().get(0).initializer().init(probe);
        assertQuad(world.getMapper(QuadDeformComponent.class).get(probe));

        HistoryManager history = new HistoryManager(32);
        IdentityRegistry registry = new IdentityRegistry();
        registry.bind(world, new games.pixscape.studio.configuration.SceneMeta());
        registry.rebuild();
        EntityGraphInstantiationResult result = new EntityGraphInstantiationService(
                world, history, registry, new games.pixscape.runtime.service.PhysicsService(
                world, null, new games.pixscape.studio.configuration.SceneMeta()))
                .instantiate(loaded, 0, 0f, 0f, "Instantiate Quad Prefab");

        Assert.assertEquals(1, result.createdIds().size);
        assertQuad(world.getMapper(QuadDeformComponent.class).get(result.createdIds().first()));
    }

    @Test
    public void loadLegacyV2PrefabMigratesWithoutQuadDeformation() {
        World world = new World(new WorldConfiguration());
        FileHandle file = tmpFile("legacy-v2.pixprefab");
        file.writeString(
                "{\"type\":\"pixscape-prefab\",\"version\":2,\"name\":\"legacy\","
                        + "\"entities\":[{\"sourceEntityId\":17}]}",
                false,
                "UTF-8"
        );

        EntityGraph loaded = new PrefabAssetService(world).loadPrefab(file);
        Assert.assertEquals(1, loaded.size());
        int probe = world.create();
        loaded.entries().get(0).initializer().init(probe);
        Assert.assertFalse(world.getMapper(QuadDeformComponent.class).has(probe));
    }

    @Test
    public void saveLoad_preservesAllAnimationAssetIdsAndActiveAsset() {
        World world = new World(new WorldConfiguration());
        int entityId = world.create();
        world.getMapper(TransformComponent.class).create(entityId);
        world.getMapper(EntityIndexComponent.class).create(entityId);
        AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).create(entityId);
        assetRef.assetId = 31;
        AnimationComponent animation = world.getMapper(AnimationComponent.class).create(entityId);
        animation.animationAssetIds.add(17);
        animation.animationAssetIds.add(31);
        animation.currentClip = "run";
        animation.fps = 24f;

        EntityGraph graph = new EntityGraphCaptureService(world).capture(arr(entityId));
        FileHandle file = tmpFile("multi-animation.pixprefab");
        PrefabAssetService service = new PrefabAssetService(world);
        service.savePrefab(file, "multi-animation", graph);
        EntityGraph loaded = service.loadPrefab(file);
        int restoredId = world.create();
        loaded.entries().get(0).initializer().init(restoredId);

        AnimationComponent restored = world.getMapper(AnimationComponent.class).get(restoredId);
        AssetRefComponent restoredRef = world.getMapper(AssetRefComponent.class).get(restoredId);
        Assert.assertArrayEquals(new int[]{17, 31}, restored.animationAssetIds.toArray());
        Assert.assertEquals(31, restoredRef.assetId);
        Assert.assertEquals("run", restored.currentClip);
        Assert.assertEquals(24f, restored.fps, 0f);
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
    public void saveLoad_roundTripsAllJointTypesAndAuthoredFields() {
        World world = new World(new WorldConfiguration());
        int bodyA = body(world);
        int bodyB = body(world);
        int bodyC = body(world);
        int bodyD = body(world);

        int distance = distanceJoint(world, bodyA, bodyB);
        int revolute = revoluteJoint(world, bodyA, bodyB);
        int prismatic = prismaticJoint(world, bodyB, bodyC);
        int wheel = wheelJoint(world, bodyC, bodyD);
        int friction = frictionJoint(world, bodyA, bodyD);
        int motor = motorJoint(world, bodyB, bodyD);
        int weld = weldJoint(world, bodyA, bodyC);
        int pulley = pulleyJoint(world, bodyB, bodyD);
        int gear = gearJoint(
                world, bodyA, bodyC, revolute, prismatic);

        int[] sourceBodies = {bodyA, bodyB, bodyC, bodyD};
        int[] sourceJoints = {
                distance, revolute, prismatic, wheel, friction,
                motor, weld, pulley, gear
        };
        int[] jointTypes = {
                PhysicsJointComponent.TYPE_DISTANCE,
                PhysicsJointComponent.TYPE_REVOLUTE,
                PhysicsJointComponent.TYPE_PRISMATIC,
                PhysicsJointComponent.TYPE_WHEEL,
                PhysicsJointComponent.TYPE_FRICTION,
                PhysicsJointComponent.TYPE_MOTOR,
                PhysicsJointComponent.TYPE_WELD,
                PhysicsJointComponent.TYPE_PULLEY,
                PhysicsJointComponent.TYPE_GEAR
        };
        int[] endpointA = {
                bodyA, bodyA, bodyB, bodyC, bodyA,
                bodyB, bodyA, bodyB, bodyA
        };
        int[] endpointB = {
                bodyB, bodyB, bodyC, bodyD, bodyD,
                bodyD, bodyC, bodyD, bodyC
        };

        for (int i = 0; i < sourceJoints.length; i++) {
            configureBaseJoint(
                    world, sourceJoints[i], i, (i & 1) == 0);
        }

        PhysicsDistanceJointComponent distanceData =
                world.getMapper(PhysicsDistanceJointComponent.class)
                        .get(distance);
        distanceData.lengthM = 3.25f;
        distanceData.frequencyHz = 4.50f;
        distanceData.dampingRatio = 0.35f;

        PhysicsRevoluteJointComponent revoluteData =
                world.getMapper(PhysicsRevoluteJointComponent.class)
                        .get(revolute);
        revoluteData.enableLimit = true;
        revoluteData.lowerAngleRad = -0.75f;
        revoluteData.upperAngleRad = 1.25f;
        revoluteData.enableMotor = true;
        revoluteData.motorSpeedRad = 2.50f;
        revoluteData.maxMotorTorque = 8.75f;

        PhysicsPrismaticJointComponent prismaticData =
                world.getMapper(PhysicsPrismaticJointComponent.class)
                        .get(prismatic);
        prismaticData.axisX = 0.60f;
        prismaticData.axisY = 0.80f;
        prismaticData.enableLimit = true;
        prismaticData.lowerTranslationM = -1.50f;
        prismaticData.upperTranslationM = 2.75f;
        prismaticData.enableMotor = true;
        prismaticData.motorSpeedMps = -3.25f;
        prismaticData.maxMotorForce = 9.50f;

        PhysicsWheelJointComponent wheelData =
                world.getMapper(PhysicsWheelJointComponent.class).get(wheel);
        wheelData.axisX = -0.80f;
        wheelData.axisY = 0.60f;
        wheelData.enableMotor = true;
        wheelData.motorSpeedRad = 4.25f;
        wheelData.maxMotorTorque = 10.50f;
        wheelData.frequencyHz = 5.75f;
        wheelData.dampingRatio = 0.45f;

        PhysicsFrictionJointComponent frictionData =
                world.getMapper(PhysicsFrictionJointComponent.class)
                        .get(friction);
        frictionData.maxForce = 11.25f;
        frictionData.maxTorque = 12.50f;

        PhysicsMotorJointComponent motorData =
                world.getMapper(PhysicsMotorJointComponent.class).get(motor);
        motorData.linearOffsetX = 1.25f;
        motorData.linearOffsetY = -2.50f;
        motorData.angularOffsetRad = 0.65f;
        motorData.maxForce = 13.75f;
        motorData.maxTorque = 14.50f;
        motorData.correctionFactor = 0.35f;

        PhysicsWeldJointComponent weldData =
                world.getMapper(PhysicsWeldJointComponent.class).get(weld);
        weldData.referenceAngleRad = -0.45f;
        weldData.frequencyHz = 6.25f;
        weldData.dampingRatio = 0.55f;

        PhysicsPulleyJointComponent pulleyData =
                world.getMapper(PhysicsPulleyJointComponent.class)
                        .get(pulley);
        pulleyData.groundAx = 10.25f;
        pulleyData.groundAy = 11.50f;
        pulleyData.groundBx = 12.75f;
        pulleyData.groundBy = 13.25f;
        pulleyData.lengthAM = 4.50f;
        pulleyData.lengthBM = 5.75f;
        pulleyData.ratio = 1.75f;

        PhysicsGearJointComponent gearData =
                world.getMapper(PhysicsGearJointComponent.class).get(gear);
        gearData.ratio = 2.25f;

        EntityGraph graph = new EntityGraphCaptureService(world)
                .capture(arr(bodyA, bodyB, bodyC, bodyD));
        Assert.assertEquals(
                "Four bodies and all nine joints should be captured",
                13, graph.size());

        FileHandle file = tmpFile("all-joints.pixprefab");
        PrefabAssetService service = new PrefabAssetService(world);
        service.savePrefab(file, "all-joints", graph);
        EntityGraph loaded = service.loadPrefab(file);

        String serialized = file.readString("UTF-8");
        JsonValue root = new JsonReader().parse(serialized);
        Assert.assertEquals("pixscape-prefab", root.getString("type"));
        Assert.assertEquals(3, root.getInt("version"));
        Assert.assertEquals("all-joints", root.getString("name"));
        String[] jointBlocks = {
                "\"joint\"", "\"distanceJoint\"", "\"revoluteJoint\"",
                "\"prismaticJoint\"", "\"wheelJoint\"",
                "\"frictionJoint\"", "\"motorJoint\"", "\"weldJoint\"",
                "\"pulleyJoint\"", "\"gearJoint\""
        };
        for (String block : jointBlocks) {
            Assert.assertTrue(
                    "Serialized prefab should contain " + block,
                    serialized.contains(block));
        }

        HistoryManager history = new HistoryManager(32);
        IdentityRegistry identities = new IdentityRegistry();
        games.pixscape.studio.configuration.SceneMeta sceneMeta =
                new games.pixscape.studio.configuration.SceneMeta();
        identities.bind(world, sceneMeta);
        identities.rebuild();

        EntityGraphInstantiationResult result =
                new EntityGraphInstantiationService(
                        world,
                        history,
                        identities,
                        new games.pixscape.runtime.service.PhysicsService(
                                world, null, sceneMeta))
                        .instantiate(
                                loaded,
                                0,
                                0f,
                                0f,
                                "Instantiate All Joint Types");

        IntSet restoredBodies = new IntSet();
        for (int sourceBody : sourceBodies) {
            int restoredBody =
                    assertMappedActiveDistinct(world, result, sourceBody);
            Assert.assertTrue(
                    "Restored bodies should be distinct",
                    restoredBodies.add(restoredBody));
        }
        Assert.assertEquals(4, restoredBodies.size);

        IntSet restoredJoints = new IntSet();
        for (int i = 0; i < sourceJoints.length; i++) {
            int restoredJoint = assertRestoredJointBase(
                    world,
                    result,
                    sourceJoints[i],
                    jointTypes[i],
                    endpointA[i],
                    endpointB[i],
                    i,
                    (i & 1) == 0);
            Assert.assertTrue(
                    "Restored joints should be distinct",
                    restoredJoints.add(restoredJoint));
            PhysicsJointComponent restoredBase =
                    world.getMapper(PhysicsJointComponent.class)
                            .get(restoredJoint);
            assertNotSourceBody(restoredBase.aEid, sourceBodies);
            assertNotSourceBody(restoredBase.bEid, sourceBodies);
            Assert.assertTrue(
                    world.getEntityManager().isActive(restoredBase.aEid));
            Assert.assertTrue(
                    world.getEntityManager().isActive(restoredBase.bEid));
        }
        Assert.assertEquals(9, restoredJoints.size);

        int restoredDistance = result.sourceToCreated().get(distance, -1);
        PhysicsDistanceJointComponent restoredDistanceData =
                world.getMapper(PhysicsDistanceJointComponent.class)
                        .get(restoredDistance);
        Assert.assertNotNull(restoredDistanceData);
        Assert.assertEquals(3.25f, restoredDistanceData.lengthM, 0.0001f);
        Assert.assertEquals(
                4.50f, restoredDistanceData.frequencyHz, 0.0001f);
        Assert.assertEquals(
                0.35f, restoredDistanceData.dampingRatio, 0.0001f);

        int restoredRevolute = result.sourceToCreated().get(revolute, -1);
        PhysicsRevoluteJointComponent restoredRevoluteData =
                world.getMapper(PhysicsRevoluteJointComponent.class)
                        .get(restoredRevolute);
        Assert.assertNotNull(restoredRevoluteData);
        Assert.assertTrue(restoredRevoluteData.enableLimit);
        Assert.assertEquals(
                -0.75f, restoredRevoluteData.lowerAngleRad, 0.0001f);
        Assert.assertEquals(
                1.25f, restoredRevoluteData.upperAngleRad, 0.0001f);
        Assert.assertTrue(restoredRevoluteData.enableMotor);
        Assert.assertEquals(
                2.50f, restoredRevoluteData.motorSpeedRad, 0.0001f);
        Assert.assertEquals(
                8.75f, restoredRevoluteData.maxMotorTorque, 0.0001f);

        int restoredPrismatic =
                result.sourceToCreated().get(prismatic, -1);
        PhysicsPrismaticJointComponent restoredPrismaticData =
                world.getMapper(PhysicsPrismaticJointComponent.class)
                        .get(restoredPrismatic);
        Assert.assertNotNull(restoredPrismaticData);
        Assert.assertEquals(
                0.60f, restoredPrismaticData.axisX, 0.0001f);
        Assert.assertEquals(
                0.80f, restoredPrismaticData.axisY, 0.0001f);
        Assert.assertTrue(restoredPrismaticData.enableLimit);
        Assert.assertEquals(
                -1.50f,
                restoredPrismaticData.lowerTranslationM,
                0.0001f);
        Assert.assertEquals(
                2.75f,
                restoredPrismaticData.upperTranslationM,
                0.0001f);
        Assert.assertTrue(restoredPrismaticData.enableMotor);
        Assert.assertEquals(
                -3.25f, restoredPrismaticData.motorSpeedMps, 0.0001f);
        Assert.assertEquals(
                9.50f, restoredPrismaticData.maxMotorForce, 0.0001f);

        int restoredWheel = result.sourceToCreated().get(wheel, -1);
        PhysicsWheelJointComponent restoredWheelData =
                world.getMapper(PhysicsWheelJointComponent.class)
                        .get(restoredWheel);
        Assert.assertNotNull(restoredWheelData);
        Assert.assertEquals(-0.80f, restoredWheelData.axisX, 0.0001f);
        Assert.assertEquals(0.60f, restoredWheelData.axisY, 0.0001f);
        Assert.assertTrue(restoredWheelData.enableMotor);
        Assert.assertEquals(
                4.25f, restoredWheelData.motorSpeedRad, 0.0001f);
        Assert.assertEquals(
                10.50f, restoredWheelData.maxMotorTorque, 0.0001f);
        Assert.assertEquals(
                5.75f, restoredWheelData.frequencyHz, 0.0001f);
        Assert.assertEquals(
                0.45f, restoredWheelData.dampingRatio, 0.0001f);

        int restoredFriction = result.sourceToCreated().get(friction, -1);
        PhysicsFrictionJointComponent restoredFrictionData =
                world.getMapper(PhysicsFrictionJointComponent.class)
                        .get(restoredFriction);
        Assert.assertNotNull(restoredFrictionData);
        Assert.assertEquals(
                11.25f, restoredFrictionData.maxForce, 0.0001f);
        Assert.assertEquals(
                12.50f, restoredFrictionData.maxTorque, 0.0001f);

        int restoredMotor = result.sourceToCreated().get(motor, -1);
        PhysicsMotorJointComponent restoredMotorData =
                world.getMapper(PhysicsMotorJointComponent.class)
                        .get(restoredMotor);
        Assert.assertNotNull(restoredMotorData);
        Assert.assertEquals(
                1.25f, restoredMotorData.linearOffsetX, 0.0001f);
        Assert.assertEquals(
                -2.50f, restoredMotorData.linearOffsetY, 0.0001f);
        Assert.assertEquals(
                0.65f, restoredMotorData.angularOffsetRad, 0.0001f);
        Assert.assertEquals(
                13.75f, restoredMotorData.maxForce, 0.0001f);
        Assert.assertEquals(
                14.50f, restoredMotorData.maxTorque, 0.0001f);
        Assert.assertEquals(
                0.35f, restoredMotorData.correctionFactor, 0.0001f);

        int restoredWeld = result.sourceToCreated().get(weld, -1);
        PhysicsWeldJointComponent restoredWeldData =
                world.getMapper(PhysicsWeldJointComponent.class)
                        .get(restoredWeld);
        Assert.assertNotNull(restoredWeldData);
        Assert.assertEquals(
                -0.45f, restoredWeldData.referenceAngleRad, 0.0001f);
        Assert.assertEquals(
                6.25f, restoredWeldData.frequencyHz, 0.0001f);
        Assert.assertEquals(
                0.55f, restoredWeldData.dampingRatio, 0.0001f);

        int restoredPulley = result.sourceToCreated().get(pulley, -1);
        PhysicsPulleyJointComponent restoredPulleyData =
                world.getMapper(PhysicsPulleyJointComponent.class)
                        .get(restoredPulley);
        Assert.assertNotNull(restoredPulleyData);
        Assert.assertEquals(
                10.25f, restoredPulleyData.groundAx, 0.0001f);
        Assert.assertEquals(
                11.50f, restoredPulleyData.groundAy, 0.0001f);
        Assert.assertEquals(
                12.75f, restoredPulleyData.groundBx, 0.0001f);
        Assert.assertEquals(
                13.25f, restoredPulleyData.groundBy, 0.0001f);
        Assert.assertEquals(
                4.50f, restoredPulleyData.lengthAM, 0.0001f);
        Assert.assertEquals(
                5.75f, restoredPulleyData.lengthBM, 0.0001f);
        Assert.assertEquals(1.75f, restoredPulleyData.ratio, 0.0001f);

        int restoredGear = result.sourceToCreated().get(gear, -1);
        PhysicsGearJointComponent restoredGearData =
                world.getMapper(PhysicsGearJointComponent.class)
                        .get(restoredGear);
        Assert.assertNotNull(restoredGearData);
        Assert.assertEquals(restoredRevolute, restoredGearData.joint1Eid);
        Assert.assertEquals(restoredPrismatic, restoredGearData.joint2Eid);
        Assert.assertEquals(2.25f, restoredGearData.ratio, 0.0001f);
        Assert.assertTrue(restoredJoints.contains(
                restoredGearData.joint1Eid));
        Assert.assertTrue(restoredJoints.contains(
                restoredGearData.joint2Eid));
        Assert.assertTrue(world.getEntityManager().isActive(
                restoredGearData.joint1Eid));
        Assert.assertTrue(world.getEntityManager().isActive(
                restoredGearData.joint2Eid));
    }

    @Test
    public void saveLoad_preservesPolygonSourceAndAllocatesFreshIdentity() {
        World world = new World(new WorldConfiguration());
        int e = body(world);

        PhysicsShapesComponent sources =
                world.getMapper(PhysicsShapesComponent.class).get(e);
        sources.shapes.clear();
        PhysicsShapeData polygon = new PhysicsShapeData();
        polygon.geometry = new PhysicsGeometryData();
        polygon.physicsShapeId = 77;
        polygon.geometry.shapeType = PhysicsGeometryData.SHAPE_POLYGON;
        polygon.geometry.polygonVertexCount = 5;
        polygon.geometry.polygonVertices = new float[]{0f, 0f, 2f, 0f, 3f, 1f, 1f, 3f, -1f, 1f};
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
        Assert.assertEquals(5, restoredPolygon.geometry.polygonVertexCount);
        Assert.assertArrayEquals(polygon.geometry.polygonVertices, restoredPolygon.geometry.polygonVertices, 0f);
        Assert.assertNotEquals(polygon.physicsShapeId, restoredPolygon.physicsShapeId);
        Assert.assertTrue(restoredPolygon.physicsShapeId > 0);
        Assert.assertNotSame(polygon, restoredPolygon);
        Assert.assertNotSame(polygon.geometry.polygonVertices, restoredPolygon.geometry.polygonVertices);
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
    public void differentVersionIsRejected() {
        World world = new World(new WorldConfiguration());
        FileHandle file = tmpFile("bad-version.pixprefab");
        file.writeString(
                "{\"type\":\"pixscape-prefab\",\"version\":1,\"entities\":[]}",
                false,
                "UTF-8"
        );

        new PrefabAssetService(world).loadPrefab(file);
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingVersionIsRejected() {
        World world = new World(new WorldConfiguration());
        FileHandle file = tmpFile("missing-version.pixprefab");
        file.writeString(
                "{\"type\":\"pixscape-prefab\",\"entities\":[]}",
                false,
                "UTF-8"
        );

        new PrefabAssetService(world).loadPrefab(file);
    }

    @Test(expected = RuntimeException.class)
    public void unknownFieldIsRejected() {
        World world = new World(new WorldConfiguration());
        FileHandle file = tmpFile("unknown-field.pixprefab");
        file.writeString(
                "{\"type\":\"pixscape-prefab\",\"version\":2,"
                        + "\"entities\":[],\"unexpected\":true}",
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

    private static void setQuad(
            QuadDeformComponent quad,
            float blX,
            float blY,
            float brX,
            float brY,
            float trX,
            float trY,
            float tlX,
            float tlY
    ) {
        quad.blX = blX;
        quad.blY = blY;
        quad.brX = brX;
        quad.brY = brY;
        quad.trX = trX;
        quad.trY = trY;
        quad.tlX = tlX;
        quad.tlY = tlY;
    }

    private static void assertQuad(QuadDeformComponent quad) {
        Assert.assertNotNull(quad);
        Assert.assertEquals(1.25f, quad.blX, 0f);
        Assert.assertEquals(-2.5f, quad.blY, 0f);
        Assert.assertEquals(3.75f, quad.brX, 0f);
        Assert.assertEquals(-4.5f, quad.brY, 0f);
        Assert.assertEquals(5.25f, quad.trX, 0f);
        Assert.assertEquals(-6.5f, quad.trY, 0f);
        Assert.assertEquals(7.75f, quad.tlX, 0f);
        Assert.assertEquals(-8.5f, quad.tlY, 0f);
    }

    private static void assertQuadJson(JsonValue quad) {
        Assert.assertEquals(1.25f, quad.getFloat("blX"), 0f);
        Assert.assertEquals(-2.5f, quad.getFloat("blY"), 0f);
        Assert.assertEquals(3.75f, quad.getFloat("brX"), 0f);
        Assert.assertEquals(-4.5f, quad.getFloat("brY"), 0f);
        Assert.assertEquals(5.25f, quad.getFloat("trX"), 0f);
        Assert.assertEquals(-6.5f, quad.getFloat("trY"), 0f);
        Assert.assertEquals(7.75f, quad.getFloat("tlX"), 0f);
        Assert.assertEquals(-8.5f, quad.getFloat("tlY"), 0f);
    }

    private static JsonValue findNamed(JsonValue value, String name) {
        if (name.equals(value.name)) {
            return value;
        }
        for (JsonValue child = value.child; child != null; child = child.next) {
            JsonValue found = findNamed(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
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
        d.geometry = new PhysicsGeometryData();
        d.physicsShapeId = e + 1;
        d.geometry.shapeType = PhysicsGeometryData.SHAPE_BOX;
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

    private static int frictionJoint(World w, int a, int b) {
        int e = base(w, PhysicsJointComponent.TYPE_FRICTION, a, b);
        w.getMapper(PhysicsFrictionJointComponent.class).create(e);
        return e;
    }

    private static int motorJoint(World w, int a, int b) {
        int e = base(w, PhysicsJointComponent.TYPE_MOTOR, a, b);
        w.getMapper(PhysicsMotorJointComponent.class).create(e);
        return e;
    }

    private static int weldJoint(World w, int a, int b) {
        int e = base(w, PhysicsJointComponent.TYPE_WELD, a, b);
        w.getMapper(PhysicsWeldJointComponent.class).create(e);
        return e;
    }

    private static int pulleyJoint(World w, int a, int b) {
        int e = base(w, PhysicsJointComponent.TYPE_PULLEY, a, b);
        w.getMapper(PhysicsPulleyJointComponent.class).create(e);
        return e;
    }

    private static void configureBaseJoint(
            World world,
            int jointEntityId,
            int ordinal,
            boolean collideConnected) {
        PhysicsJointComponent joint =
                world.getMapper(PhysicsJointComponent.class)
                        .get(jointEntityId);
        joint.collideConnected = collideConnected;
        joint.anchorAx = ordinal + 1.10f;
        joint.anchorAy = -(ordinal + 1.20f);
        joint.anchorBx = ordinal + 2.30f;
        joint.anchorBy = -(ordinal + 2.40f);
    }

    private static int assertRestoredJointBase(
            World world,
            EntityGraphInstantiationResult result,
            int sourceJointId,
            int expectedType,
            int sourceBodyA,
            int sourceBodyB,
            int ordinal,
            boolean collideConnected) {
        int restoredJoint =
                assertMappedActiveDistinct(world, result, sourceJointId);
        PhysicsJointComponent joint =
                world.getMapper(PhysicsJointComponent.class)
                        .get(restoredJoint);
        Assert.assertNotNull(
                "Restored joint should have PhysicsJointComponent",
                joint);
        Assert.assertEquals(expectedType, joint.type);
        Assert.assertEquals(
                result.sourceToCreated().get(sourceBodyA, -1),
                joint.aEid);
        Assert.assertEquals(
                result.sourceToCreated().get(sourceBodyB, -1),
                joint.bEid);
        Assert.assertEquals(collideConnected, joint.collideConnected);
        Assert.assertEquals(
                ordinal + 1.10f, joint.anchorAx, 0.0001f);
        Assert.assertEquals(
                -(ordinal + 1.20f), joint.anchorAy, 0.0001f);
        Assert.assertEquals(
                ordinal + 2.30f, joint.anchorBx, 0.0001f);
        Assert.assertEquals(
                -(ordinal + 2.40f), joint.anchorBy, 0.0001f);
        return restoredJoint;
    }

    private static int assertMappedActiveDistinct(
            World world,
            EntityGraphInstantiationResult result,
            int sourceEntityId) {
        Assert.assertTrue(
                "sourceToCreated should contain source entity "
                        + sourceEntityId,
                result.sourceToCreated().containsKey(sourceEntityId));
        int restored =
                result.sourceToCreated().get(sourceEntityId, -1);
        Assert.assertTrue(restored >= 0);
        Assert.assertNotEquals(sourceEntityId, restored);
        Assert.assertTrue(world.getEntityManager().isActive(restored));
        return restored;
    }

    private static void assertNotSourceBody(
            int entityId, int[] sourceBodies) {
        for (int sourceBody : sourceBodies) {
            Assert.assertNotEquals(sourceBody, entityId);
        }
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
