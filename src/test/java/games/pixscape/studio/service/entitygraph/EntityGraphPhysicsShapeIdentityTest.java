package games.pixscape.studio.service.entitygraph;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class EntityGraphPhysicsShapeIdentityTest {
    @Test
    public void instantiationAllocatesEachCopiedShapeExactlyOnce() {
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Main");
        ProjectConfig.setInstance(config);

        World world = new World(new WorldConfiguration());
        int sourceEntity = world.create();
        world.getMapper(TransformComponent.class).create(sourceEntity);
        world.getMapper(EntityIndexComponent.class).create(sourceEntity);
        world.getMapper(PhysicsBodyComponent.class).create(sourceEntity);
        PhysicsShapesComponent sourceShapes =
                world.getMapper(PhysicsShapesComponent.class).create(sourceEntity);
        PhysicsShapeData source = new PhysicsShapeData();
        source.physicsShapeId = 42;
        source.shapeType = PhysicsShapeData.SHAPE_CIRCLE;
        source.radius = 3f;
        sourceShapes.add(source);

        IntArray selection = new IntArray();
        selection.add(sourceEntity);
        EntityGraph graph = new EntityGraphCaptureService(world).capture(selection);
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world);
        identities.rebuild();
        EntityGraphInstantiationResult result =
                new EntityGraphInstantiationService(
                        world, new HistoryManager(16), identities)
                        .instantiate(graph, 0, 0f, 0f, "Copy");

        int created = result.createdIds().first();
        PhysicsShapeData copied = world.getMapper(PhysicsShapesComponent.class)
                .get(created).shapes.first();
        Assert.assertEquals(1, copied.physicsShapeId);
        Assert.assertEquals(3f, copied.radius, 0f);
        Assert.assertEquals(2,
                config.getCurrentSceneMeta().nextPhysicsShapeId);
        Assert.assertEquals(42, source.physicsShapeId);
    }
}
