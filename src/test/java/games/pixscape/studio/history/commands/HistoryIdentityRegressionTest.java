package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.entitygraph.EntityGraph;
import games.pixscape.studio.service.entitygraph.EntityGraphCaptureService;
import games.pixscape.studio.service.entitygraph.EntityGraphInstantiationResult;
import games.pixscape.studio.service.entitygraph.EntityGraphInstantiationService;
import org.junit.Assert;
import org.junit.Test;

public class HistoryIdentityRegressionTest {

    @Test
    public void createUndoRedoCyclesPreserveHistoryIdentityWithoutDuplicatingStableIdentity() {
        World world = world();
        HistoryManager history = new HistoryManager(16);
        IdentityRegistry identities = bindIdentities(world);
        int[] currentEntity = {-1};

        GenericEntityInitializer init = spriteInitializer(world, 100, 11, "Created Sprite");
        init.setIdentityStableId(1001);

        CreateEntityCommand command = new CreateEntityCommand(
                world,
                history.historyIds(),
                init,
                eid -> currentEntity[0] = eid
        );
        history.execute(command);
        world.process();

        long historyId = history.historyIds().historyIdOfEntity(currentEntity[0]);
        Assert.assertTrue(historyId > 0L);

        for (int i = 0; i < 5; i++) {
            history.undo();
            world.process();
            Assert.assertEquals(-1, history.historyIds().entityOfHistoryId(historyId));

            history.redo();
            world.process();
            int restored = history.historyIds().entityOfHistoryId(historyId);
            Assert.assertTrue(restored >= 0);
            Assert.assertTrue(world.getEntityManager().isActive(restored));
            Assert.assertEquals(historyId, history.historyIds().historyIdOfEntity(restored));
            assertStableId(world, restored, 1001);
            assertRenderBindingOrFallback(world, restored);
            assertNoDuplicateStableIds(world, identities);
        }
    }

    @Test
    public void deleteUndoRedoCyclesDoNotRestoreDuplicatePersistentIdentities() {
        World world = world();
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(16);
        IdentityRegistry identities = bindIdentities(world);

        int a = createSprite(world, historyIds, 101, 2001);
        int b = createSprite(world, historyIds, 102, 2002);
        int c = createPlainEntity(world, historyIds, 2003);
        long bHistoryId = historyIds.historyIdOfEntity(b);

        history.execute(new DeleteEntitiesCommand(world, historyIds, arr(b)));
        world.process();

        for (int i = 0; i < 5; i++) {
            history.undo();
            world.process();
            int restored = historyIds.entityOfHistoryId(bHistoryId);
            Assert.assertTrue(restored >= 0);
            Assert.assertEquals(bHistoryId, historyIds.historyIdOfEntity(restored));
            assertStableId(world, restored, 2002);
            assertNoDuplicateStableIds(world, identities);
            assertUniqueHistoryMappings(world, historyIds, bHistoryId);

            history.redo();
            world.process();
            Assert.assertEquals(-1, historyIds.entityOfHistoryId(bHistoryId));
            assertNoDuplicateStableIds(world, identities);
        }

        Assert.assertTrue(world.getEntityManager().isActive(a));
        Assert.assertTrue(world.getEntityManager().isActive(c));
    }

    @Test
    public void mixedDeleteRestorePreservesHistoryIdentityForEveryRestoredEntity() {
        World world = world();
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(16);
        IdentityRegistry identities = bindIdentities(world);

        int sprite = createSprite(world, historyIds, 201, 3001);
        int animation = createAnimation(world, historyIds, 202, 3002);
        int plain = createPlainEntity(world, historyIds, 3003);
        int physics = createPhysicsOnlyEntity(world, historyIds, 3004);
        long spriteHistoryId = historyIds.historyIdOfEntity(sprite);
        long animationHistoryId = historyIds.historyIdOfEntity(animation);
        long plainHistoryId = historyIds.historyIdOfEntity(plain);
        long physicsHistoryId = historyIds.historyIdOfEntity(physics);

        history.execute(new DeleteEntitiesCommand(world, historyIds, arr(sprite, animation, plain, physics)));
        world.process();
        history.undo();
        world.process();
        history.redo();
        world.process();
        history.undo();
        world.process();

        assertRestored(world, historyIds, spriteHistoryId, 3001, true);
        assertRestored(world, historyIds, animationHistoryId, 3002, true);
        assertRestored(world, historyIds, plainHistoryId, 3003, false);
        assertRestored(world, historyIds, physicsHistoryId, 3004, false);
        assertNoDuplicateStableIds(world, identities);
        assertUniqueHistoryMappings(world, historyIds, spriteHistoryId, animationHistoryId, plainHistoryId, physicsHistoryId);
    }

    @Test
    public void prefabInstantiationAssignsFreshIdentitiesForEachInstance() {
        World world = world();
        HistoryManager history = new HistoryManager(32);
        IdentityRegistry identities = bindIdentities(world);

        int sourceA = createSprite(world, history.historyIds(), 301, 4001);
        int sourceB = createPlainEntity(world, history.historyIds(), 4002);
        int sourceC = createPhysicsOnlyEntity(world, history.historyIds(), 4003);
        identities.rebuild();

        EntityGraph graph = new EntityGraphCaptureService(world).capture(arr(sourceA, sourceB, sourceC));
        EntityGraphInstantiationService service = new EntityGraphInstantiationService(world, history, identities);

        EntityGraphInstantiationResult first = service.instantiate(graph, 0, 10f, 0f, "Instantiate Prefab");
        world.process();
        EntityGraphInstantiationResult second = service.instantiate(graph, 0, 20f, 0f, "Instantiate Prefab");
        world.process();

        Assert.assertEquals(3, first.createdIds().size);
        Assert.assertEquals(3, second.createdIds().size);
        assertNoDuplicateStableIds(world, identities);
        assertNoStableIdOverlap(world, arr(sourceA, sourceB, sourceC), first.createdIds(), second.createdIds());
        assertAllHaveHistoryIds(world, history.historyIds(), first.createdIds());
        assertAllHaveHistoryIds(world, history.historyIds(), second.createdIds());
    }

    @Test
    public void prefabDeleteUndoRedoStressDoesNotDuplicateIdentityMappings() {
        World world = world();
        HistoryManager history = new HistoryManager(32);
        IdentityRegistry identities = bindIdentities(world);

        int sourceA = createSprite(world, history.historyIds(), 401, 5001);
        int sourceB = createPlainEntity(world, history.historyIds(), 5002);
        identities.rebuild();

        EntityGraph graph = new EntityGraphCaptureService(world).capture(arr(sourceA, sourceB));
        EntityGraphInstantiationResult instance = new EntityGraphInstantiationService(world, history, identities)
                .instantiate(graph, 0, 10f, 10f, "Instantiate Prefab");
        world.process();

        long firstHistoryId = history.historyIds().historyIdOfEntity(instance.createdIds().get(0));
        long secondHistoryId = history.historyIds().historyIdOfEntity(instance.createdIds().get(1));

        history.execute(new DeleteEntitiesCommand(world, history.historyIds(), instance.createdIds()));
        world.process();

        for (int i = 0; i < 5; i++) {
            history.undo();
            world.process();
            assertUniqueHistoryMappings(world, history.historyIds(), firstHistoryId, secondHistoryId);
            assertNoDuplicateStableIds(world, identities);

            history.redo();
            world.process();
            Assert.assertEquals(-1, history.historyIds().entityOfHistoryId(firstHistoryId));
            Assert.assertEquals(-1, history.historyIds().entityOfHistoryId(secondHistoryId));
            assertNoDuplicateStableIds(world, identities);
        }
    }

    @Test
    public void executingNewCommandAfterUndoClearsRedoStackAndPreventsOldIdentityReintroduction() {
        World world = world();
        HistoryManager history = new HistoryManager(16);
        IdentityRegistry identities = bindIdentities(world);

        int[] created = {-1};
        history.execute(createSpriteCommand(world, history.historyIds(), 501, 6001, created));
        world.process();
        long aHistoryId = history.historyIds().historyIdOfEntity(created[0]);

        history.execute(createSpriteCommand(world, history.historyIds(), 502, 6002, created));
        world.process();
        long bHistoryId = history.historyIds().historyIdOfEntity(created[0]);

        history.execute(createSpriteCommand(world, history.historyIds(), 503, 6003, created));
        world.process();
        long cHistoryId = history.historyIds().historyIdOfEntity(created[0]);

        history.undo();
        world.process();
        history.undo();
        world.process();
        Assert.assertTrue(history.canRedo());

        history.execute(createSpriteCommand(world, history.historyIds(), 504, 6004, created));
        world.process();
        long dHistoryId = history.historyIds().historyIdOfEntity(created[0]);

        Assert.assertFalse(history.canRedo());
        history.redo();
        world.process();

        Assert.assertTrue(world.getEntityManager().isActive(history.historyIds().entityOfHistoryId(aHistoryId)));
        Assert.assertEquals(-1, history.historyIds().entityOfHistoryId(bHistoryId));
        Assert.assertEquals(-1, history.historyIds().entityOfHistoryId(cHistoryId));
        Assert.assertTrue(world.getEntityManager().isActive(history.historyIds().entityOfHistoryId(dHistoryId)));
        assertNoDuplicateStableIds(world, identities);
    }

    private static World world() {
        return games.pixscape.studio.FixtureIdentityTestSupport.newWorld();
    }

    private static IdentityRegistry bindIdentities(World world) {
        IdentityRegistry registry = new IdentityRegistry();
        registry.bind(world);
        registry.rebuild();
        return registry;
    }

    private static CreateEntityCommand createSpriteCommand(World world,
                                                           HistoryIdRegistry historyIds,
                                                           int assetId,
                                                           int stableId,
                                                           int[] createdOut) {
        GenericEntityInitializer init = spriteInitializer(world, assetId, stableId, "Sprite " + stableId);
        init.setIdentityStableId(stableId);
        return new CreateEntityCommand(world, historyIds, init, eid -> createdOut[0] = eid);
    }

    private static GenericEntityInitializer spriteInitializer(World world, int assetId, int stableId, String name) {
        GenericEntityInitializer init = new GenericEntityInitializer(world)
                .configureStandaloneSprite(assetId, "main", 16, 16, assetId, assetId * 2f, 8f, 8f, 0, 0, 10 + assetId, name, 0);
        init.setIdentityStableId(stableId);
        return init;
    }

    private static int createSprite(World world, HistoryIdRegistry historyIds, int assetId, int stableId) {
        int entityId = world.create();
        spriteInitializer(world, assetId, stableId, "Sprite " + stableId).init(entityId);
        historyIds.ensureForEntity(entityId);
        return entityId;
    }

    private static int createAnimation(World world, HistoryIdRegistry historyIds, int assetId, int stableId) {
        int entityId = createSprite(world, historyIds, assetId, stableId);
        EntityMetaComponent meta = world.getMapper(EntityMetaComponent.class).get(entityId);
        meta.kind = EntityKind.ANIMATION;
        AnimationComponent animation = world.getMapper(AnimationComponent.class).create(entityId);
        animation.animation = "walk";
        animation.currentClip = "default";
        animation.fps = 12f;
        animation.playing = true;
        animation.loop = true;
        animation.clips.put("default", new AnimationComponent.Clip(0, 3));
        return entityId;
    }

    private static int createPlainEntity(World world, HistoryIdRegistry historyIds, int stableId) {
        int entityId = world.create();
        TransformComponent transform = world.getMapper(TransformComponent.class).create(entityId);
        transform.x = stableId;
        transform.y = stableId * 0.5f;
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entityId);
        index.layerIndex = 0;
        index.zIndex = stableId;
        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).create(entityId);
        identity.stableId = stableId;
        identity.name = "Plain " + stableId;
        EntityMetaComponent meta = world.getMapper(EntityMetaComponent.class).create(entityId);
        meta.kind = EntityKind.UNKNOWN;
        historyIds.ensureForEntity(entityId);
        return entityId;
    }

    private static int createPhysicsOnlyEntity(World world, HistoryIdRegistry historyIds, int stableId) {
        int entityId = createPlainEntity(world, historyIds, stableId);
        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).create(entityId);
        PhysicsService.initDefaultBody(body);
        PhysicsFixturesComponent fixtures = world.getMapper(PhysicsFixturesComponent.class).create(entityId);
        FixtureDefData fixture = games.pixscape.studio.FixtureIdentityTestSupport.createFixture(world);
        fixture.shapeType = FixtureDefData.SHAPE_BOX;
        fixtures.fixtures.add(fixture);
        return entityId;
    }

    private static IntArray arr(int... ids) {
        IntArray out = new IntArray();
        for (int id : ids) {
            out.add(id);
        }
        return out;
    }

    private static void assertRestored(World world,
                                       HistoryIdRegistry historyIds,
                                       long historyId,
                                       int stableId,
                                       boolean renderExpected) {
        int entityId = historyIds.entityOfHistoryId(historyId);
        Assert.assertTrue(entityId >= 0);
        Assert.assertTrue(world.getEntityManager().isActive(entityId));
        Assert.assertEquals(historyId, historyIds.historyIdOfEntity(entityId));
        assertStableId(world, entityId, stableId);
        if (renderExpected) {
            assertRenderBindingOrFallback(world, entityId);
        }
    }

    private static void assertStableId(World world, int entityId, int expectedStableId) {
        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).get(entityId);
        Assert.assertEquals(expectedStableId, identity.stableId);
    }

    private static void assertRenderBindingOrFallback(World world, int entityId) {
        TextureRegionComponent region = world.getMapper(TextureRegionComponent.class).getSafe(entityId, null);
        RenderMaterialComponent material = world.getMapper(RenderMaterialComponent.class).getSafe(entityId, null);
        AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).getSafe(entityId, null);

        Assert.assertNotNull("Render entity should keep material after restore", material);
        boolean hasResolvedBinding = region != null && region.valid && material.textureHandle != 0;
        boolean hasFallbackSource = assetRef != null && assetRef.assetId >= 0 && assetRef.atlasTag != null && !assetRef.atlasTag.isEmpty();
        Assert.assertTrue("Render entity should have resolved binding or fallback asset source", hasResolvedBinding || hasFallbackSource);
    }

    private static void assertNoDuplicateStableIds(World world, IdentityRegistry identities) {
        identities.rebuild();

        IntSet seen = new IntSet();
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(PixscapeIdentityComponent.class))
                .getEntities();
        int[] data = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) {
            int entityId = data[i];
            if (!world.getEntityManager().isActive(entityId)) continue;

            PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).get(entityId);
            if (identity.stableId == IdentityRegistry.UNASSIGNED_STABLE_ID) continue;

            Assert.assertFalse("Duplicate stableId " + identity.stableId, seen.contains(identity.stableId));
            seen.add(identity.stableId);
            Assert.assertEquals(entityId, identities.findByStableId(identity.stableId));
        }
    }

    private static void assertUniqueHistoryMappings(World world, HistoryIdRegistry historyIds, long... expectedHistoryIds) {
        IntSet seenEntities = new IntSet();
        for (long historyId : expectedHistoryIds) {
            int entityId = historyIds.entityOfHistoryId(historyId);
            Assert.assertTrue(entityId >= 0);
            Assert.assertTrue(world.getEntityManager().isActive(entityId));
            Assert.assertFalse("Duplicate history mapping for entity " + entityId, seenEntities.contains(entityId));
            seenEntities.add(entityId);
            Assert.assertEquals(historyId, historyIds.historyIdOfEntity(entityId));
        }
    }

    private static void assertAllHaveHistoryIds(World world, HistoryIdRegistry historyIds, IntArray entityIds) {
        for (int i = 0; i < entityIds.size; i++) {
            int entityId = entityIds.get(i);
            Assert.assertTrue(world.getEntityManager().isActive(entityId));
            Assert.assertTrue(historyIds.historyIdOfEntity(entityId) > 0L);
        }
    }

    private static void assertNoStableIdOverlap(World world, IntArray originals, IntArray first, IntArray second) {
        ObjectMap<Integer, Integer> owners = new ObjectMap<>();
        assertFreshStableIds(world, owners, originals);
        assertFreshStableIds(world, owners, first);
        assertFreshStableIds(world, owners, second);
    }

    private static void assertFreshStableIds(World world, ObjectMap<Integer, Integer> owners, IntArray entityIds) {
        for (int i = 0; i < entityIds.size; i++) {
            int entityId = entityIds.get(i);
            PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).get(entityId);
            Assert.assertTrue(identity.stableId != IdentityRegistry.UNASSIGNED_STABLE_ID);
            Integer previous = owners.put(identity.stableId, entityId);
            Assert.assertNull("Stable id reused by entities " + previous + " and " + entityId, previous);
        }
    }
}
