package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.spatial.SpatialBlockProjection;

final class SpatialBlockPhysicsSync {
    private static final int SPATIAL_BLOCK_FIXTURE_ID_BASE = 1_000_000;

    private SpatialBlockPhysicsSync() {
    }

    static void sync(World world, int layerEntityId, SpatialBlockData block, Object source) {
        if (world == null || layerEntityId < 0 || block == null || block.id <= 0) return;

        ComponentMapper<TiledLayerComponent> mTiled = world.getMapper(TiledLayerComponent.class);
        TiledLayerComponent tiled = mTiled.getSafe(layerEntityId, null);
        if (tiled == null || tiled.data == null) {
            removeGeneratedFixture(world, layerEntityId, block.id, source);
            return;
        }

        if (!block.enabled || !block.physicsCollision) {
            removeGeneratedFixture(world, layerEntityId, block.id, source);
            return;
        }

        ensureStaticBody(world, layerEntityId);
        tiled.data.collisionEnabled = true;

        PhysicsFixturesComponent fixtures =
                world.getMapper(PhysicsFixturesComponent.class).getSafe(layerEntityId, null);
        if (fixtures == null) return;

        int fixtureId = fixtureIdForBlock(block.id);
        FixtureDefData fixture = findFixture(fixtures, fixtureId);
        if (fixture == null) {
            fixture = FixtureCommandSupport.createDefaultFixture();
            fixture.fixtureId = fixtureId;
            fixtures.fixtures.add(fixture);
        }

        applyFootprintPolygon(world, layerEntityId, tiled.data, block, fixture);
        markPhysicsChanged(world, layerEntityId, source);
    }

    static int fixtureIdForBlock(int blockId) {
        return SPATIAL_BLOCK_FIXTURE_ID_BASE + Math.max(1, blockId);
    }

    static void removeBlockFixture(World world, int layerEntityId, int blockId, Object source) {
        removeGeneratedFixture(world, layerEntityId, blockId, source);
    }

    static LayerPhysicsState captureLayerPhysics(World world, int layerEntityId) {
        return LayerPhysicsState.capture(world, layerEntityId);
    }

    private static void ensureStaticBody(World world, int layerEntityId) {
        ComponentMapper<PhysicsBodyComponent> mBody = world.getMapper(PhysicsBodyComponent.class);
        ComponentMapper<PhysicsFixturesComponent> mFixtures = world.getMapper(PhysicsFixturesComponent.class);
        ComponentMapper<TransformComponent> mTransform = world.getMapper(TransformComponent.class);

        PhysicsBodyComponent body =
                mBody.has(layerEntityId) ? mBody.get(layerEntityId) : mBody.create(layerEntityId);
        body.type = PhysicsBodyComponent.STATIC;
        body.fixedRotation = false;
        body.bullet = false;
        body.allowSleep = true;
        body.awake = true;
        body.gravityScale = 1f;
        body.linearDamping = 0f;
        body.angularDamping = 0f;
        body.enabled = true;

        if (!mFixtures.has(layerEntityId)) {
            mFixtures.create(layerEntityId);
        }

        if (!mTransform.has(layerEntityId)) {
            TransformComponent transform = mTransform.create(layerEntityId);
            transform.x = 0f;
            transform.y = 0f;
            transform.rotationRad = 0f;
            transform.scaleX = 1f;
            transform.scaleY = 1f;
        }
    }

    private static void removeGeneratedFixture(World world, int layerEntityId, int blockId, Object source) {
        PhysicsFixturesComponent fixtures =
                world.getMapper(PhysicsFixturesComponent.class).getSafe(layerEntityId, null);
        if (fixtures == null || fixtures.fixtures == null) return;

        int index = indexOfFixture(fixtures, fixtureIdForBlock(blockId));
        if (index < 0) return;

        fixtures.fixtures.removeIndex(index);
        markPhysicsChanged(world, layerEntityId, source);
    }

    private static void applyFootprintPolygon(World world,
                                              int layerEntityId,
                                              TiledMapLayerData map,
                                              SpatialBlockData block,
                                              FixtureDefData fixture) {
        float[] worldVerts = new float[8];
        SpatialBlockProjection.projectBaseFootprint(map, block, worldVerts);

        TransformComponent transform =
                world.getMapper(TransformComponent.class).getSafe(layerEntityId, null);
        float ppm = pixelsPerMeter();
        float cos = transform != null ? MathUtils.cos(transform.rotationRad) : 1f;
        float sin = transform != null ? MathUtils.sin(transform.rotationRad) : 0f;
        float tx = transform != null ? transform.x : 0f;
        float ty = transform != null ? transform.y : 0f;

        float[] localMeters = new float[8];
        for (int i = 0; i < 4; i++) {
            float dx = worldVerts[i * 2] - tx;
            float dy = worldVerts[i * 2 + 1] - ty;
            localMeters[i * 2] = (dx * cos + dy * sin) / ppm;
            localMeters[i * 2 + 1] = (-dx * sin + dy * cos) / ppm;
        }

        fixture.shapeType = FixtureDefData.SHAPE_POLYGON;
        fixture.polyCount = 4;
        fixture.polyVerts = localMeters;
        fixture.offsetX = 0f;
        fixture.offsetY = 0f;
        fixture.angleDeg = 0f;
        fixture.radius = 0.5f;
        fixture.halfW = 0.5f;
        fixture.halfH = 0.5f;
    }

    private static FixtureDefData findFixture(PhysicsFixturesComponent fixtures, int fixtureId) {
        int index = indexOfFixture(fixtures, fixtureId);
        return index >= 0 ? fixtures.fixtures.get(index) : null;
    }

    private static int indexOfFixture(PhysicsFixturesComponent fixtures, int fixtureId) {
        if (fixtures == null || fixtures.fixtures == null || fixtureId <= 0) return -1;
        for (int i = 0; i < fixtures.fixtures.size; i++) {
            FixtureDefData fixture = fixtures.fixtures.get(i);
            if (fixture != null && fixture.fixtureId == fixtureId) return i;
        }
        return -1;
    }

    private static float pixelsPerMeter() {
        ProjectConfig config = ProjectConfig.getInstance();
        SceneMeta meta = config != null ? config.getCurrentSceneMeta() : null;
        return meta != null && meta.pixelsPerMeter > 0f ? meta.pixelsPerMeter : 100f;
    }

    private static void markPhysicsChanged(World world, int layerEntityId, Object source) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.physics(layerEntityId, PhysicsDirtyBits.ALL);
        }
        EventFlow.i().publish(new EventFlow.PhysicsBodyStructureChanged(layerEntityId, EventFlow.tag(source)));
    }

    static final class LayerPhysicsState {
        private final boolean hadBody;
        private final BodyState body;
        private final boolean hadFixtures;
        private final Array<FixtureDefData> fixtures = new Array<>();
        private final boolean hadTransform;
        private final TransformState transform;
        private final boolean hadTiledData;
        private final boolean collisionEnabled;

        private LayerPhysicsState(boolean hadBody,
                                  BodyState body,
                                  boolean hadFixtures,
                                  boolean hadTransform,
                                  TransformState transform,
                                  boolean hadTiledData,
                                  boolean collisionEnabled) {
            this.hadBody = hadBody;
            this.body = body;
            this.hadFixtures = hadFixtures;
            this.hadTransform = hadTransform;
            this.transform = transform;
            this.hadTiledData = hadTiledData;
            this.collisionEnabled = collisionEnabled;
        }

        private static LayerPhysicsState capture(World world, int layerEntityId) {
            if (world == null || layerEntityId < 0) {
                return new LayerPhysicsState(false, null, false, false, null, false, false);
            }

            PhysicsBodyComponent body =
                    world.getMapper(PhysicsBodyComponent.class).getSafe(layerEntityId, null);
            PhysicsFixturesComponent fixtures =
                    world.getMapper(PhysicsFixturesComponent.class).getSafe(layerEntityId, null);
            TransformComponent transform =
                    world.getMapper(TransformComponent.class).getSafe(layerEntityId, null);
            TiledLayerComponent tiled =
                    world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null);

            LayerPhysicsState state = new LayerPhysicsState(
                    body != null,
                    body != null ? BodyState.capture(body) : null,
                    fixtures != null,
                    transform != null,
                    transform != null ? TransformState.capture(transform) : null,
                    tiled != null && tiled.data != null,
                    tiled != null && tiled.data != null && tiled.data.collisionEnabled
            );

            if (fixtures != null && fixtures.fixtures != null) {
                for (FixtureDefData fixture : fixtures.fixtures) {
                    if (fixture != null) state.fixtures.add(fixture.copy());
                }
            }
            return state;
        }

        void restore(World world, int layerEntityId, Object source) {
            if (world == null || layerEntityId < 0) return;

            ComponentMapper<PhysicsBodyComponent> mBody = world.getMapper(PhysicsBodyComponent.class);
            ComponentMapper<PhysicsFixturesComponent> mFixtures = world.getMapper(PhysicsFixturesComponent.class);
            ComponentMapper<TransformComponent> mTransform = world.getMapper(TransformComponent.class);
            ComponentMapper<PhysicsRuntimeBodyComponent> mRuntime = world.getMapper(PhysicsRuntimeBodyComponent.class);

            if (hadBody) {
                PhysicsBodyComponent target =
                        mBody.has(layerEntityId) ? mBody.get(layerEntityId) : mBody.create(layerEntityId);
                if (body != null) body.apply(target);
            } else {
                if (mBody.has(layerEntityId)) mBody.remove(layerEntityId);
                if (mRuntime.has(layerEntityId)) mRuntime.remove(layerEntityId);
            }

            if (hadFixtures) {
                PhysicsFixturesComponent target =
                        mFixtures.has(layerEntityId) ? mFixtures.get(layerEntityId) : mFixtures.create(layerEntityId);
                target.fixtures.clear();
                for (FixtureDefData fixture : fixtures) {
                    if (fixture != null) target.fixtures.add(fixture.copy());
                }
            } else if (mFixtures.has(layerEntityId)) {
                mFixtures.remove(layerEntityId);
            }

            if (hadTransform) {
                TransformComponent target =
                        mTransform.has(layerEntityId) ? mTransform.get(layerEntityId) : mTransform.create(layerEntityId);
                if (transform != null) transform.apply(target);
            } else if (!hadBody && mTransform.has(layerEntityId)) {
                mTransform.remove(layerEntityId);
            }

            TiledLayerComponent tiled =
                    world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null);
            if (hadTiledData && tiled != null && tiled.data != null) {
                tiled.data.collisionEnabled = collisionEnabled;
            }

            markPhysicsChanged(world, layerEntityId, source);
        }
    }

    private static final class BodyState {
        int type;
        boolean fixedRotation;
        boolean bullet;
        boolean allowSleep;
        boolean awake;
        float gravityScale;
        float linearDamping;
        float angularDamping;
        boolean enabled;

        static BodyState capture(PhysicsBodyComponent body) {
            BodyState state = new BodyState();
            state.type = body.type;
            state.fixedRotation = body.fixedRotation;
            state.bullet = body.bullet;
            state.allowSleep = body.allowSleep;
            state.awake = body.awake;
            state.gravityScale = body.gravityScale;
            state.linearDamping = body.linearDamping;
            state.angularDamping = body.angularDamping;
            state.enabled = body.enabled;
            return state;
        }

        void apply(PhysicsBodyComponent body) {
            body.type = type;
            body.fixedRotation = fixedRotation;
            body.bullet = bullet;
            body.allowSleep = allowSleep;
            body.awake = awake;
            body.gravityScale = gravityScale;
            body.linearDamping = linearDamping;
            body.angularDamping = angularDamping;
            body.enabled = enabled;
        }
    }

    private static final class TransformState {
        float x;
        float y;
        float originX;
        float originY;
        float rotationRad;
        float scaleX;
        float scaleY;

        static TransformState capture(TransformComponent transform) {
            TransformState state = new TransformState();
            state.x = transform.x;
            state.y = transform.y;
            state.originX = transform.originX;
            state.originY = transform.originY;
            state.rotationRad = transform.rotationRad;
            state.scaleX = transform.scaleX;
            state.scaleY = transform.scaleY;
            return state;
        }

        void apply(TransformComponent transform) {
            transform.x = x;
            transform.y = y;
            transform.originX = originX;
            transform.originY = originY;
            transform.rotationRad = rotationRad;
            transform.scaleX = scaleX;
            transform.scaleY = scaleY;
            transform.cos = MathUtils.cos(rotationRad);
            transform.sin = MathUtils.sin(rotationRad);
            transform.absCos = Math.abs(transform.cos);
            transform.absSin = Math.abs(transform.sin);
            transform.invScaleX = scaleX != 0f ? 1f / scaleX : 1f;
            transform.invScaleY = scaleY != 0f ? 1f / scaleY : 1f;
        }
    }
}
