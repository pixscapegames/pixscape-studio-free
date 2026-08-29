package games.pixscape.studio.service.entitygraph;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

public class EntityGraphPhysicsEligibilityTest {

    @Test
    public void physicsPrefabInstantiatesInOrdinaryLayerWhenScenePhysicsIsEnabled() {
        Fixture fixture = new Fixture(true);
        int source = fixture.createEntity(true);
        EntityGraph graph = fixture.capture(source);

        EntityGraphInstantiationResult result = fixture.service.instantiatePrefab(
                graph, 0, 0f, 0f, "Drop Physics Prefab", 7, "physics-prefab");

        Assert.assertEquals(1, result.createdIds().size);
        int created = result.createdIds().first();
        Assert.assertTrue(fixture.world.getMapper(PhysicsBodyComponent.class).has(created));
        Assert.assertEquals(1,
                fixture.world.getMapper(PhysicsShapesComponent.class).get(created).shapes.size);
        Assert.assertEquals(1, fixture.history.getCursor());
    }

    @Test
    public void physicsGraphIsRejectedBeforeAnyMutationWhenScenePhysicsIsDisabled() {
        Fixture fixture = new Fixture(false);
        int source = fixture.createEntity(true);
        EntityGraph graph = fixture.capture(source);
        int entitiesBefore = fixture.entityCount();
        int historyCursorBefore = fixture.history.getCursor();
        int nextStableIdBefore = fixture.sceneMeta.nextEntityStableId;
        int nextShapeIdBefore = fixture.sceneMeta.nextPhysicsShapeId;

        EntityGraphInstantiationResult result = fixture.service.instantiatePrefab(
                graph, 0, 0f, 0f, "Drop Physics Prefab", 7, "physics-prefab");

        Assert.assertEquals(0, result.createdIds().size);
        Assert.assertEquals(entitiesBefore, fixture.entityCount());
        Assert.assertEquals(historyCursorBefore, fixture.history.getCursor());
        Assert.assertEquals(nextStableIdBefore, fixture.sceneMeta.nextEntityStableId);
        Assert.assertEquals(nextShapeIdBefore, fixture.sceneMeta.nextPhysicsShapeId);
        Assert.assertFalse(fixture.history.canUndo());
    }

    @Test
    public void ordinaryPrefabInstantiatesWhenScenePhysicsIsDisabled() {
        Fixture fixture = new Fixture(false);
        int source = fixture.createEntity(false);
        EntityGraph graph = fixture.capture(source);

        EntityGraphInstantiationResult result = fixture.service.instantiatePrefab(
                graph, 0, 0f, 0f, "Drop Ordinary Prefab", 8, "ordinary-prefab");

        Assert.assertEquals(1, result.createdIds().size);
        int created = result.createdIds().first();
        Assert.assertFalse(fixture.world.getMapper(PhysicsBodyComponent.class).has(created));
        Assert.assertEquals(1, fixture.history.getCursor());
    }

    private static final class Fixture {
        final World world = new World(new WorldConfiguration());
        final HistoryManager history = new HistoryManager(16);
        final SceneMeta sceneMeta = new SceneMeta();
        final IdentityRegistry identities = new IdentityRegistry();
        final AtomicBoolean physicsEnabled;
        final EntityGraphInstantiationService service;

        Fixture(boolean physicsEnabled) {
            this.physicsEnabled = new AtomicBoolean(physicsEnabled);
            sceneMeta.physicsEnabled = physicsEnabled;
            identities.bind(world, sceneMeta);
            identities.rebuild();
            int layerEntity = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(layerEntity);
            layer.layerIndex = 0;
            world.getMapper(LayerMetaComponent.class).create(layerEntity).name = "Ordinary";
            world.process();
            service = new EntityGraphInstantiationService(
                    world,
                    history,
                    identities,
                    new PhysicsService(world, null, sceneMeta),
                    this.physicsEnabled::get);
        }

        int createEntity(boolean physical) {
            int entity = world.create();
            world.getMapper(TransformComponent.class).create(entity);
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
            index.layerIndex = 3;
            if (physical) {
                world.getMapper(PhysicsBodyComponent.class).create(entity);
                PhysicsShapesComponent shapes =
                        world.getMapper(PhysicsShapesComponent.class).create(entity);
                PhysicsShapeData shape = new PhysicsShapeData();
                shape.physicsShapeId = 91;
                shape.geometry = new PhysicsGeometryData();
                shape.geometry.shapeType = PhysicsGeometryData.SHAPE_BOX;
                shapes.shapes.add(shape);
            }
            world.process();
            return entity;
        }

        EntityGraph capture(int entity) {
            return new EntityGraphCaptureService(world)
                    .captureForPrefab(new IntArray(new int[]{entity}));
        }

        int entityCount() {
            return world.getAspectSubscriptionManager()
                    .get(Aspect.all()).getEntities().size();
        }
    }
}
