package games.pixscape.studio.service.entitygraph;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class EntityGraphJointPrecommitValidationTest {
    @Before
    public void activateSceneAllocator() {
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Main");
        ProjectConfig.setInstance(config);
    }

    @Test
    public void everyInvalidJointGraphFailsBeforeHistoryExecution() {
        for (InvalidGraph invalidGraph : InvalidGraph.values()) {
            Fixture fixture = new Fixture();
            try {
                invalidGraph.mutate(fixture);
                EntityGraph graph = fixture.capturePreparedGraph();
                int entitiesBefore = fixture.count(Aspect.all());
                int jointsBefore =
                        fixture.count(Aspect.all(PhysicsJointComponent.class));
                int cursorBefore = fixture.history.getCursor();
                fixture.sentinel.processCount = 0;

                try {
                    fixture.service.instantiate(
                            graph, 0, 0f, 0f, "Invalid graph");
                    Assert.fail(invalidGraph + " must fail during preparation.");
                } catch (IllegalArgumentException expected) {
                    Assert.assertTrue(
                            invalidGraph + " must provide a joint diagnostic.",
                            expected.getMessage().contains("joint")
                                    || expected.getMessage().contains("Joint"));
                }

                Assert.assertEquals(
                        invalidGraph + " created entities.",
                        entitiesBefore,
                        fixture.count(Aspect.all()));
                Assert.assertEquals(
                        invalidGraph + " created partial joints.",
                        jointsBefore,
                        fixture.count(Aspect.all(PhysicsJointComponent.class)));
                Assert.assertEquals(cursorBefore, fixture.history.getCursor());
                Assert.assertFalse(fixture.history.canUndo());
                Assert.assertFalse(fixture.history.canRedo());
                Assert.assertEquals(
                        invalidGraph + " processed the World.",
                        0,
                        fixture.sentinel.processCount);
            } finally {
                fixture.world.dispose();
            }
        }
    }

    private enum InvalidGraph {
        DISTANCE_WITHOUT_SPECIFIC {
            @Override
            void mutate(Fixture f) {
                f.revoluteBase.type = PhysicsJointComponent.TYPE_DISTANCE;
            }
        },
        UNKNOWN_TYPE {
            @Override
            void mutate(Fixture f) {
                f.revoluteBase.type = 999;
            }
        },
        ENDPOINT_WITHOUT_BODY {
            @Override
            void mutate(Fixture f) {
                f.revoluteBase.aEid = f.gearEntity;
            }
        },
        ENDPOINT_WITHOUT_SHAPES {
            @Override
            void mutate(Fixture f) {
                f.world.getMapper(PhysicsShapesComponent.class).remove(f.bodyA);
            }
        },
        GEAR_SELF_REFERENCE {
            @Override
            void mutate(Fixture f) {
                f.gear.joint1Eid = f.gearEntity;
            }
        },
        GEAR_DUPLICATE_DEPENDENCY {
            @Override
            void mutate(Fixture f) {
                f.gear.joint2Eid = f.gear.joint1Eid;
            }
        },
        GEAR_REFERENCES_DISTANCE {
            @Override
            void mutate(Fixture f) {
                f.revoluteBase.type = PhysicsJointComponent.TYPE_DISTANCE;
                f.world.getMapper(PhysicsRevoluteJointComponent.class)
                        .remove(f.revoluteEntity);
                f.world.getMapper(PhysicsDistanceJointComponent.class)
                        .create(f.revoluteEntity);
            }
        },
        GEAR_REFERENCES_GEAR {
            @Override
            void mutate(Fixture f) {
                int otherGear = f.createGear(
                        f.bodyA,
                        f.bodyC,
                        f.revoluteEntity,
                        f.prismaticEntity);
                f.gear.joint1Eid = otherGear;
            }
        },
        GEAR_REFERENCES_NON_JOINT {
            @Override
            void mutate(Fixture f) {
                f.gear.joint1Eid = f.bodyA;
            }
        },
        GEAR_SOURCE_WITHOUT_SPECIFIC {
            @Override
            void mutate(Fixture f) {
                f.world.getMapper(PhysicsRevoluteJointComponent.class)
                        .remove(f.revoluteEntity);
            }
        };

        abstract void mutate(Fixture fixture);
    }

    private static final class Fixture {
        final SentinelSystem sentinel = new SentinelSystem();
        final World world = new World(new WorldConfigurationBuilder()
                .with(sentinel)
                .build());
        final HistoryManager history = new HistoryManager(32);
        final IdentityRegistry identities = new IdentityRegistry();
        final EntityGraphInstantiationService service;
        final List<Integer> entities = new ArrayList<>();
        int nextSourceShapeId = 1;
        final int bodyA = createBody();
        final int bodyB = createBody();
        final int bodyC = createBody();
        final int revoluteEntity =
                createJoint(PhysicsJointComponent.TYPE_REVOLUTE, bodyA, bodyB);
        final PhysicsJointComponent revoluteBase =
                world.getMapper(PhysicsJointComponent.class).get(revoluteEntity);
        final int prismaticEntity =
                createJoint(PhysicsJointComponent.TYPE_PRISMATIC, bodyB, bodyC);
        final int gearEntity =
                createGear(bodyA, bodyC, revoluteEntity, prismaticEntity);
        final PhysicsGearJointComponent gear =
                world.getMapper(PhysicsGearJointComponent.class).get(gearEntity);

        Fixture() {
            identities.bind(world, new games.pixscape.studio.configuration.SceneMeta());
            identities.rebuild();
            service =
                    new EntityGraphInstantiationService(
                            world, history, identities,
                            new games.pixscape.runtime.service.PhysicsService(
                                    world, null, new games.pixscape.studio.configuration.SceneMeta()), () -> true);
            world.process();
            sentinel.processCount = 0;
        }

        int createBody() {
            int entityId = world.create();
            entities.add(entityId);
            world.getMapper(TransformComponent.class).create(entityId);
            world.getMapper(EntityIndexComponent.class).create(entityId);
            world.getMapper(PhysicsBodyComponent.class).create(entityId);
            PhysicsShapesComponent shapes =
                    world.getMapper(PhysicsShapesComponent.class).create(entityId);
            PhysicsShapeData shape = new PhysicsShapeData();
            shape.geometry = new PhysicsGeometryData();
            shape.physicsShapeId = nextSourceShapeId++;
            shape.geometry.shapeType = PhysicsGeometryData.SHAPE_BOX;
            shapes.shapes.add(shape);
            return entityId;
        }

        int createJoint(int type, int bodyAId, int bodyBId) {
            int entityId = world.create();
            entities.add(entityId);
            PhysicsJointComponent base =
                    world.getMapper(PhysicsJointComponent.class).create(entityId);
            base.type = type;
            base.aEid = bodyAId;
            base.bEid = bodyBId;
            if (type == PhysicsJointComponent.TYPE_REVOLUTE) {
                world.getMapper(PhysicsRevoluteJointComponent.class)
                        .create(entityId);
            } else if (type == PhysicsJointComponent.TYPE_PRISMATIC) {
                world.getMapper(PhysicsPrismaticJointComponent.class)
                        .create(entityId);
            }
            return entityId;
        }

        int createGear(
                int bodyAId,
                int bodyBId,
                int joint1EntityId,
                int joint2EntityId) {
            int entityId =
                    createJoint(PhysicsJointComponent.TYPE_GEAR, bodyAId, bodyBId);
            PhysicsGearJointComponent gear =
                    world.getMapper(PhysicsGearJointComponent.class)
                            .create(entityId);
            gear.joint1Eid = joint1EntityId;
            gear.joint2Eid = joint2EntityId;
            return entityId;
        }

        EntityGraph capturePreparedGraph() {
            List<EntityGraphEntry> entries = new ArrayList<>();
            for (int entityId : entities) {
                GenericEntityInitializer initializer =
                        new GenericEntityInitializer(world);
                initializer.syncFrom(entityId);
                entries.add(new EntityGraphEntry(entityId, initializer));
            }
            return new EntityGraph(entries);
        }

        int count(Aspect.Builder aspect) {
            return world.getAspectSubscriptionManager()
                    .get(aspect)
                    .getEntities()
                    .size();
        }
    }

    private static final class SentinelSystem extends BaseSystem {
        int processCount;

        @Override
        protected void processSystem() {
            processCount++;
        }
    }
}
