package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.hierarchy.GameObjectTransformMath;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.AbstractCommonInitializer;
import games.pixscape.studio.model.EntityKind;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CreateGameObjectChildCommandTest {
    @Test public void createsSpriteChild() { assertCreates(EntityKind.SPRITE); }
    @Test public void createsAnimationChild() { assertCreates(EntityKind.ANIMATION); }
    @Test public void createsPointLightChild() { assertCreates(EntityKind.POINT_LIGHT); }
    @Test public void createsConeLightChild() { assertCreates(EntityKind.CONE_LIGHT); }
    @Test public void createsNestedGameObjectChild() { assertCreates(EntityKind.GAME_OBJECT); }

    @Test
    public void firstVisualChildEstablishesStablePivotWithoutChangingItsWorldPose() {
        for (EntityKind kind : new EntityKind[]{
                EntityKind.SPRITE,
                EntityKind.ANIMATION,
                EntityKind.POINT_LIGHT,
                EntityKind.CONE_LIGHT}) {
            assertFirstVisualChildEstablishesPivot(kind);
        }
    }

    @Test
    public void nestedFirstVisualChildMovesOnlyTheNestedPivotInParentSpace() {
        Fixture fixture = new Fixture();
        try {
            int top = fixture.root(10, 6, 12);
            fixture.setTransform(top, 100f, -40f, 0.35f, 2f, 2f);
            int nested = fixture.root(11, 6, 0);
            fixture.setTransform(nested, 7f, -3f, -0.2f, 1f, 1f);
            fixture.world.getMapper(GameObjectMemberComponent.class)
                    .create(nested).parentStableId = 10;
            fixture.process();

            TransformComponent topBefore = copy(fixture.transform(top));
            VisualChildInitializer initializer = new VisualChildInitializer(
                    fixture.world, EntityKind.SPRITE,
                    31f, 47f, 0.6f, 1.25f, 0.75f,
                    3f, 5f, 30f, 18f);
            initializer.setIdentityStableId(30);
            TransformComponent originalChildWorld = initializer.transformSnapshot();
            float[] expectedLocalBounds = visualBounds(
                    originalChildWorld, 30f, 18f, fixture.transform(top));

            CreateGameObjectChildCommand command = new CreateGameObjectChildCommand(
                    fixture.world, fixture.history.historyIds(), fixture.identities,
                    nested, initializer, null);
            fixture.history.execute(command);
            fixture.process();

            assertTransformEquals(topBefore, fixture.transform(top));
            TransformComponent nestedWorld = GameObjectTransformMath.localToWorld(
                    fixture.transform(top), fixture.transform(nested), true,
                    new TransformComponent());
            TransformComponent nestedLocal = fixture.transform(nested);
            assertEquals(expectedLocalBounds[0], nestedLocal.x, 0.0001f);
            assertEquals(expectedLocalBounds[1], nestedLocal.y, 0.0001f);
            assertEquals(
                    (expectedLocalBounds[2] - expectedLocalBounds[0]) * 0.5f,
                    nestedLocal.originX, 0.0001f);
            assertEquals(
                    (expectedLocalBounds[3] - expectedLocalBounds[1]) * 0.5f,
                    nestedLocal.originY, 0.0001f);
            TransformComponent childWorld = GameObjectTransformMath.localToWorld(
                    nestedWorld, fixture.transform(command.getCreatedEntityId()),
                    new TransformComponent());
            assertTransformEquals(originalChildWorld, childWorld);
        } finally {
            fixture.close();
        }
    }

    @Test
    public void laterChildAddAndRemovalDoNotRecenterEstablishedPivot() {
        Fixture fixture = new Fixture();
        try {
            int parent = fixture.root(10, 6, 12);
            fixture.process();
            VisualChildInitializer firstInitializer = new VisualChildInitializer(
                    fixture.world, EntityKind.SPRITE,
                    20f, 30f, 0f, 1f, 1f,
                    0f, 0f, 40f, 20f);
            firstInitializer.setIdentityStableId(30);
            CreateGameObjectChildCommand first = new CreateGameObjectChildCommand(
                    fixture.world, fixture.history.historyIds(), fixture.identities,
                    parent, firstInitializer, null);
            fixture.history.execute(first);
            fixture.process();
            TransformComponent established = copy(fixture.transform(parent));

            VisualChildInitializer secondInitializer = new VisualChildInitializer(
                    fixture.world, EntityKind.ANIMATION,
                    -300f, 500f, 0.9f, 1f, 1f,
                    0f, 0f, 100f, 80f);
            secondInitializer.setIdentityStableId(31);
            CreateGameObjectChildCommand second = new CreateGameObjectChildCommand(
                    fixture.world, fixture.history.historyIds(), fixture.identities,
                    parent, secondInitializer, null);
            fixture.history.execute(second);
            fixture.process();
            assertTransformEquals(established, fixture.transform(parent));

            fixture.transform(second.getCreatedEntityId()).x += 123f;
            fixture.transform(second.getCreatedEntityId()).refreshCaches();
            fixture.process();
            assertTransformEquals(established, fixture.transform(parent));

            fixture.history.undo();
            fixture.process();
            assertTransformEquals(established, fixture.transform(parent));
        } finally {
            fixture.close();
        }
    }

    @Test
    public void rootRotationAndUniformScaleRemainCenteredOnEstablishedPivot() {
        Fixture fixture = new Fixture();
        try {
            int parent = fixture.root(10, 6, 12);
            fixture.process();
            VisualChildInitializer initializer = new VisualChildInitializer(
                    fixture.world, EntityKind.SPRITE,
                    15f, 25f, 0.2f, 1.1f, 0.8f,
                    30f, 13f, 60f, 26f);
            initializer.setIdentityStableId(30);
            CreateGameObjectChildCommand command = new CreateGameObjectChildCommand(
                    fixture.world, fixture.history.historyIds(), fixture.identities,
                    parent, initializer, null);
            fixture.history.execute(command);
            fixture.process();

            TransformComponent root = fixture.transform(parent);
            TransformComponent childLocal = fixture.transform(command.getCreatedEntityId());
            root.rotationRad += 1.1f;
            root.scaleX = 2.75f;
            root.scaleY = 2.75f;
            root.refreshCaches();
            TransformComponent childWorld = GameObjectTransformMath.localToWorld(
                    root, childLocal, new TransformComponent());
            assertEquals(root.x + root.originX,
                    visualCenterX(childWorld, 60f, 26f), 0.0001f);
            assertEquals(root.y + root.originY,
                    visualCenterY(childWorld, 60f, 26f), 0.0001f);
        } finally {
            fixture.close();
        }
    }

    @Test
    public void nestedParentUsesItsOwnStableIdAndTheTopLevelEffectiveLayer() {
        Fixture fixture = new Fixture();
        try {
            fixture.root(10, 6, 12);
            int nested = fixture.root(11, 6, 0);
            fixture.world.getMapper(GameObjectMemberComponent.class)
                    .create(nested).parentStableId = 10;
            fixture.process();
            TestChildInitializer initializer = new TestChildInitializer(
                    fixture.world, EntityKind.SPRITE);
            initializer.setIdentityStableId(30);
            CreateGameObjectChildCommand command = new CreateGameObjectChildCommand(
                    fixture.world,
                    fixture.history.historyIds(),
                    fixture.identities,
                    nested,
                    initializer,
                    null);

            fixture.history.execute(command);
            fixture.process();
            int child = command.getCreatedEntityId();
            assertEquals(11, fixture.world.getMapper(GameObjectMemberComponent.class)
                    .get(child).parentStableId);
            assertEquals(6, fixture.world.getMapper(EntityIndexComponent.class)
                    .get(child).layerIndex);
        } finally {
            fixture.close();
        }
    }

    private static void assertCreates(EntityKind kind) {
        Fixture fixture = new Fixture();
        try {
            int parent = fixture.root(10, 6, 12);
            fixture.existingChild(20, 10, 6, 3);
            fixture.process();

            TestChildInitializer initializer = new TestChildInitializer(fixture.world, kind);
            initializer.setIdentityStableId(30);
            int[] selected = {-1};
            CreateGameObjectChildCommand command = new CreateGameObjectChildCommand(
                    fixture.world,
                    fixture.history.historyIds(),
                    fixture.identities,
                    parent,
                    initializer,
                    entityId -> selected[0] = entityId);

            fixture.history.execute(command);
            fixture.process();
            int child = selected[0];
            long historyId = fixture.history.historyIds().historyIdOfEntity(child);
            assertTrue(child >= 0);
            assertTrue(historyId > 0L);
            assertEquals(10, fixture.world.getMapper(GameObjectMemberComponent.class)
                    .get(child).parentStableId);
            assertEquals(6, fixture.world.getMapper(EntityIndexComponent.class).get(child).layerIndex);
            assertEquals(4, fixture.world.getMapper(EntityIndexComponent.class).get(child).zIndex);
            TransformComponent local = fixture.world.getMapper(TransformComponent.class).get(child);
            assertEquals(0f, local.x, 0f);
            assertEquals(0f, local.y, 0f);
            assertEquals(0f, local.rotationRad, 0f);
            assertEquals(1f, local.scaleX, 0f);
            assertEquals(1f, local.scaleY, 0f);
            assertMarker(fixture.world, child, kind);

            fixture.history.undo();
            fixture.process();
            assertEquals(-1, fixture.history.historyIds().entityOfHistoryId(historyId));
            assertTrue(fixture.identities.findByStableId(20) >= 0);
            assertFalse(fixture.history.canUndo());
            assertTrue(fixture.history.canRedo());

            fixture.history.redo();
            fixture.process();
            int restored = fixture.history.historyIds().entityOfHistoryId(historyId);
            assertTrue(restored >= 0);
            assertEquals(restored, selected[0]);
            assertEquals(30, fixture.world.getMapper(PixscapeIdentityComponent.class)
                    .get(restored).stableId);
            assertEquals(10, fixture.world.getMapper(GameObjectMemberComponent.class)
                    .get(restored).parentStableId);
            assertEquals(4, fixture.world.getMapper(EntityIndexComponent.class).get(restored).zIndex);
            assertMarker(fixture.world, restored, kind);
        } finally {
            fixture.close();
        }
    }

    private static void assertFirstVisualChildEstablishesPivot(EntityKind kind) {
        Fixture fixture = new Fixture();
        try {
            int parent = fixture.root(10, 6, 12);
            fixture.setTransform(parent, 100f, -50f, 0.4f, 2f, 2f);
            fixture.process();
            TransformComponent rootBefore = copy(fixture.transform(parent));
            VisualChildInitializer initializer = new VisualChildInitializer(
                    fixture.world, kind,
                    10f, 12f, 0.3f, 1.5f, 0.75f,
                    4f, 6f, 80f, 40f);
            initializer.setIdentityStableId(30);
            TransformComponent originalChildWorld = initializer.transformSnapshot();
            float[] expectedBounds = visualBounds(originalChildWorld, 80f, 40f, null);
            CreateGameObjectChildCommand command = new CreateGameObjectChildCommand(
                    fixture.world, fixture.history.historyIds(), fixture.identities,
                    parent, initializer, null);

            fixture.history.execute(command);
            fixture.process();
            TransformComponent rootAfter = copy(fixture.transform(parent));
            TransformComponent childLocalAfter = copy(
                    fixture.transform(command.getCreatedEntityId()));
            assertEquals(expectedBounds[0], rootAfter.x, 0.0001f);
            assertEquals(expectedBounds[1], rootAfter.y, 0.0001f);
            assertEquals(
                    (expectedBounds[2] - expectedBounds[0]) * 0.5f,
                    rootAfter.originX, 0.0001f);
            assertEquals(
                    (expectedBounds[3] - expectedBounds[1]) * 0.5f,
                    rootAfter.originY, 0.0001f);
            assertEquals(rootBefore.rotationRad, rootAfter.rotationRad, 0f);
            assertEquals(rootBefore.scaleX, rootAfter.scaleX, 0f);
            assertEquals(rootBefore.scaleY, rootAfter.scaleY, 0f);
            TransformComponent reconstructed = GameObjectTransformMath.localToWorld(
                    rootAfter, childLocalAfter, new TransformComponent());
            assertTransformEquals(originalChildWorld, reconstructed);

            fixture.history.undo();
            fixture.process();
            assertTransformEquals(rootBefore, fixture.transform(parent));
            assertTrue(fixture.history.canRedo());

            for (int cycle = 0; cycle < 3; cycle++) {
                fixture.history.redo();
                fixture.process();
                assertTransformEquals(rootAfter, fixture.transform(parent));
                int restored = command.getCreatedEntityId();
                assertTransformEquals(childLocalAfter, fixture.transform(restored));
                reconstructed = GameObjectTransformMath.localToWorld(
                        fixture.transform(parent), fixture.transform(restored),
                        new TransformComponent());
                assertTransformEquals(originalChildWorld, reconstructed);
                fixture.history.undo();
                fixture.process();
                assertTransformEquals(rootBefore, fixture.transform(parent));
            }
        } finally {
            fixture.close();
        }
    }

    private static float visualCenterX(
            TransformComponent transform, float width, float height) {
        return games.pixscape.studio.helper.AuthoredGeometryTransform.worldX(
                transform, width * 0.5f, height * 0.5f);
    }

    private static float visualCenterY(
            TransformComponent transform, float width, float height) {
        return games.pixscape.studio.helper.AuthoredGeometryTransform.worldY(
                transform, width * 0.5f, height * 0.5f);
    }

    private static float[] visualBounds(
            TransformComponent transform, float width, float height,
            TransformComponent coordinateParent) {
        float[] bounds = {
                Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY
        };
        include(bounds, transform, coordinateParent, 0f, 0f);
        include(bounds, transform, coordinateParent, width, 0f);
        include(bounds, transform, coordinateParent, width, height);
        include(bounds, transform, coordinateParent, 0f, height);
        return bounds;
    }

    private static void include(
            float[] bounds,
            TransformComponent transform,
            TransformComponent coordinateParent,
            float localX,
            float localY) {
        float x = games.pixscape.studio.helper.AuthoredGeometryTransform.worldX(
                transform, localX, localY);
        float y = games.pixscape.studio.helper.AuthoredGeometryTransform.worldY(
                transform, localX, localY);
        if (coordinateParent != null) {
            float p00 = coordinateParent.cos * coordinateParent.scaleX;
            float p01 = -coordinateParent.sin * coordinateParent.scaleY;
            float p10 = coordinateParent.sin * coordinateParent.scaleX;
            float p11 = coordinateParent.cos * coordinateParent.scaleY;
            float frameX = coordinateParent.x + coordinateParent.originX
                    - p00 * coordinateParent.originX - p01 * coordinateParent.originY;
            float frameY = coordinateParent.y + coordinateParent.originY
                    - p10 * coordinateParent.originX - p11 * coordinateParent.originY;
            float determinant = p00 * p11 - p01 * p10;
            float dx = x - frameX;
            float dy = y - frameY;
            x = (p11 * dx - p01 * dy) / determinant;
            y = (-p10 * dx + p00 * dy) / determinant;
        }
        bounds[0] = Math.min(bounds[0], x);
        bounds[1] = Math.min(bounds[1], y);
        bounds[2] = Math.max(bounds[2], x);
        bounds[3] = Math.max(bounds[3], y);
    }

    private static TransformComponent copy(TransformComponent source) {
        TransformComponent copy = new TransformComponent();
        copy.x = source.x;
        copy.y = source.y;
        copy.originX = source.originX;
        copy.originY = source.originY;
        copy.rotationRad = source.rotationRad;
        copy.scaleX = source.scaleX;
        copy.scaleY = source.scaleY;
        copy.refreshCaches();
        return copy;
    }

    private static void assertTransformEquals(
            TransformComponent expected, TransformComponent actual) {
        assertEquals(expected.x, actual.x, 0.0001f);
        assertEquals(expected.y, actual.y, 0.0001f);
        assertEquals(expected.originX, actual.originX, 0.0001f);
        assertEquals(expected.originY, actual.originY, 0.0001f);
        assertEquals(expected.rotationRad, actual.rotationRad, 0.0001f);
        assertEquals(expected.scaleX, actual.scaleX, 0.0001f);
        assertEquals(expected.scaleY, actual.scaleY, 0.0001f);
    }

    private static void assertMarker(World world, int entityId, EntityKind kind) {
        assertEquals(kind, world.getMapper(EntityMetaComponent.class).get(entityId).kind);
        assertEquals(kind == EntityKind.ANIMATION,
                world.getMapper(AnimationComponent.class).has(entityId));
        assertEquals(kind == EntityKind.POINT_LIGHT,
                world.getMapper(PointLightComponent.class).has(entityId));
        assertEquals(kind == EntityKind.CONE_LIGHT,
                world.getMapper(ConeLightComponent.class).has(entityId));
        assertEquals(kind == EntityKind.GAME_OBJECT,
                world.getMapper(GameObjectComponent.class).has(entityId));
    }

    private static final class TestChildInitializer extends AbstractCommonInitializer {
        private final EntityKind kind;

        private TestChildInitializer(World world, EntityKind kind) {
            super(world);
            this.kind = kind;
            hasTransform = true;
            trX = 7f;
            trY = -9f;
            trRotationRad = 0.75f;
            trScaleX = 1f;
            trScaleY = 1f;
            hasEntityIndex = true;
            entityLayerIndex = 99;
            entityZIndex = 99;
            hasIdentity = true;
            identityName = kind.name();
            hasMeta = true;
            metaKind = kind;
            metaNoteSnapshot = "";
        }

        @Override
        public void init(int entityId) {
            super.init(entityId);
            switch (kind) {
                case ANIMATION -> world.getMapper(AnimationComponent.class).create(entityId);
                case POINT_LIGHT -> world.getMapper(PointLightComponent.class).create(entityId);
                case CONE_LIGHT -> world.getMapper(ConeLightComponent.class).create(entityId);
                case GAME_OBJECT -> world.getMapper(GameObjectComponent.class).create(entityId);
                default -> { }
            }
        }

        @Override
        public String label() {
            return kind == EntityKind.GAME_OBJECT ? "Game Object" : kind.name();
        }
    }

    private static final class VisualChildInitializer extends AbstractCommonInitializer {
        private final EntityKind kind;
        private final float width;
        private final float height;

        private VisualChildInitializer(
                World world,
                EntityKind kind,
                float x,
                float y,
                float rotation,
                float scaleX,
                float scaleY,
                float originX,
                float originY,
                float width,
                float height) {
            super(world);
            this.kind = kind;
            this.width = width;
            this.height = height;
            hasTransform = true;
            trX = x;
            trY = y;
            trRotationRad = rotation;
            trScaleX = scaleX;
            trScaleY = scaleY;
            trOriginX = originX;
            trOriginY = originY;
            hasEntityIndex = true;
            entityLayerIndex = 99;
            entityZIndex = 99;
            hasIdentity = true;
            identityName = kind.name();
            hasMeta = true;
            metaKind = kind;
            metaNoteSnapshot = "";
        }

        @Override
        public void init(int entityId) {
            super.init(entityId);
            DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class)
                    .create(entityId);
            dimensions.width = width;
            dimensions.height = height;
            switch (kind) {
                case SPRITE -> world.getMapper(TextureRegionComponent.class).create(entityId);
                case ANIMATION -> {
                    world.getMapper(TextureRegionComponent.class).create(entityId);
                    world.getMapper(AnimationComponent.class).create(entityId);
                }
                case POINT_LIGHT -> world.getMapper(PointLightComponent.class).create(entityId);
                case CONE_LIGHT -> world.getMapper(ConeLightComponent.class).create(entityId);
                default -> throw new IllegalArgumentException("Visual test kind required.");
            }
        }

        private TransformComponent transformSnapshot() {
            TransformComponent transform = new TransformComponent();
            transform.x = trX;
            transform.y = trY;
            transform.originX = trOriginX;
            transform.originY = trOriginY;
            transform.rotationRad = trRotationRad;
            transform.scaleX = trScaleX;
            transform.scaleY = trScaleY;
            transform.refreshCaches();
            return transform;
        }

        @Override
        public String label() {
            return kind.name();
        }
    }

    private static final class Fixture {
        final World world = new World();
        final HistoryManager history = new HistoryManager(8);
        final IdentityRegistry identities = new IdentityRegistry();

        private Fixture() {
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.nextEntityStableId = 100;
            identities.bind(world, meta);
        }

        int root(int stableId, int layer, int z) {
            int entityId = core(stableId, layer, z);
            world.getMapper(GameObjectComponent.class).create(entityId).sourceAssetId = "";
            return entityId;
        }

        void existingChild(int stableId, int parentStableId, int layer, int z) {
            int entityId = core(stableId, layer, z);
            world.getMapper(GameObjectMemberComponent.class)
                    .create(entityId).parentStableId = parentStableId;
        }

        int core(int stableId, int layer, int z) {
            int entityId = world.create();
            PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class)
                    .create(entityId);
            identity.stableId = stableId;
            identity.name = "Entity " + stableId;
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class)
                    .create(entityId);
            index.layerIndex = layer;
            index.zIndex = z;
            TransformComponent transform = world.getMapper(TransformComponent.class)
                    .create(entityId);
            transform.scaleX = 1f;
            transform.scaleY = 1f;
            transform.refreshCaches();
            return entityId;
        }

        void process() {
            world.process();
            identities.rebuild();
        }

        TransformComponent transform(int entityId) {
            return world.getMapper(TransformComponent.class).get(entityId);
        }

        void setTransform(
                int entityId,
                float x,
                float y,
                float rotation,
                float scaleX,
                float scaleY) {
            TransformComponent transform = transform(entityId);
            transform.x = x;
            transform.y = y;
            transform.rotationRad = rotation;
            transform.scaleX = scaleX;
            transform.scaleY = scaleY;
            transform.refreshCaches();
        }

        void close() {
            identities.bind(null, null);
            world.dispose();
        }
    }
}
