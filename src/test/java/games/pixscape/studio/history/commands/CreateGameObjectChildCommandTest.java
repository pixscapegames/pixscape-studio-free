package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.hierarchy.GameObjectTransformMath;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.AbstractCommonInitializer;
import games.pixscape.studio.model.EntityKind;
import org.junit.Test;

import static org.junit.Assert.*;

public class CreateGameObjectChildCommandTest {
    @Test
    public void inheritedOverridePreservesWorldCircleAndSubtreeRemovalRestoresEveryOwner() {
        Fixture f = new Fixture();
        try {
            int layer = f.world.create();
            f.world.getMapper(LayerComponent.class).create(layer).spatialEnabled = true;
            int root = f.root(10, 0, 0), middle = f.root(11, 0, 0), child = f.root(12, 0, 0);
            f.world.getMapper(GameObjectMemberComponent.class).create(middle).parentStableId = 10;
            f.world.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = 11;
            int grandchild = f.core(13, 0, 0);
            f.world.getMapper(GameObjectMemberComponent.class).create(grandchild).parentStableId = 12;
            f.setTransform(root, 60, -37, .7f, 2, 2);
            f.setTransform(middle, 18, 20, -.3f, 1.5f, 1.5f);
            f.setTransform(child, -24, 9, .5f, 1, 1);
            var footprints = f.world.getMapper(games.pixscape.runtime.component.spatial.SpatialFootprintComponent.class);
            var parentFoot = footprints.create(root);
            parentFoot.radiusPx = 13.5f;
            parentFoot.altitude = 17.25f;
            parentFoot.localOffsetXPx = 9;
            parentFoot.localOffsetYPx = -6;
            var representations = f.world.getMapper(games.pixscape.runtime.component.spatial.SpatialRepresentationComponent.class);
            var settings = new games.pixscape.runtime.spatial.ActorSpatialRectangles();
            var rectangle = new games.pixscape.runtime.spatial.ActorSpatialRectangleData();
            rectangle.width = .35f;
            rectangle.pivotX = -7;
            settings.put(0, null, rectangle);
            representations.create(child).rectangles = settings;
            var body = f.world.getMapper(games.pixscape.runtime.component.physics.PhysicsBodyComponent.class).create(child);
            body.gravityScale = 4;
            f.process();
            var before = new games.pixscape.runtime.spatial.SpatialActorGeometry.Footprint();
            assertTrue(games.pixscape.runtime.service.SpatialActorPose.resolve(root,
                    GameObjectHierarchyCommandSupport.worldTransform(f.world, f.identities, root), parentFoot, null, before));
            f.history.execute(new SetFootprintInheritanceCommand(f.world, f.history.historyIds(), child, false));
            var own = footprints.get(child).copy();
            var after = new games.pixscape.runtime.spatial.SpatialActorGeometry.Footprint();
            assertTrue(games.pixscape.runtime.service.SpatialActorPose.resolve(child,
                    GameObjectHierarchyCommandSupport.worldTransform(f.world, f.identities, child), footprints.get(child), null, after));
            assertEquals(before.footX, after.footX, .0001f);
            assertEquals(before.footY, after.footY, .0001f);
            assertEquals(parentFoot.radiusPx, own.radiusPx, 0);
            assertEquals(parentFoot.altitude, own.altitude, 0);
            assertEquals(child, games.pixscape.studio.service.SpatialActorShapeSupport.ancestorOwner(f.world, grandchild));
            f.history.execute(new SetFootprintInheritanceCommand(f.world, f.history.historyIds(), child, true));
            for (int cycle = 0; cycle < 3; cycle++) {
                assertFalse(footprints.has(child));
                f.history.undo();
                assertEquals(own.localOffsetXPx, footprints.get(child).localOffsetXPx, 0);
                f.history.redo();
            }
            f.history.undo(); // Restore the own override before removing the whole spatial composition.
            f.history.execute(new RemoveSpatialSubtreeCommand(f.world, f.history.historyIds(), root));
            for (int cycle = 0; cycle < 3; cycle++) {
                assertFalse(footprints.has(root));
                assertFalse(footprints.has(child));
                assertEquals(-1, games.pixscape.studio.service.SpatialActorShapeSupport.ancestorOwner(f.world, grandchild));
                assertSame(settings, representations.get(child).rectangles);
                assertSame(body, f.world.getMapper(games.pixscape.runtime.component.physics.PhysicsBodyComponent.class).get(child));
                assertEquals(4, body.gravityScale, 0);
                f.history.undo();
                assertEquals(13.5f, footprints.get(root).radiusPx, 0);
                assertEquals(own.localOffsetYPx, footprints.get(child).localOffsetYPx, 0);
                assertEquals(child, games.pixscape.studio.service.SpatialActorShapeSupport.ancestorOwner(f.world, grandchild));
                f.history.redo();
            }
        } finally {
            f.close();
        }
    }

    @Test
    public void visualChildCreationInitializesItsRepresentationWithinTheSameHistoryEntry() {
        Fixture fixture = new Fixture();
        try {
            int parent = fixture.root(10, 0, 0);
            fixture.world.getMapper(games.pixscape.runtime.component.spatial.SpatialFootprintComponent.class).create(parent);
            int template = fixture.core(20, 0, 1);
            fixture.world.getMapper(TextureRegionComponent.class).create(template);
            var size = fixture.world.getMapper(DimensionsComponent.class).create(template);
            size.width = 80;
            size.height = 40;
            fixture.process();
            var initializer = new games.pixscape.studio.history.initializer.GenericEntityInitializer(fixture.world);
            initializer.syncFrom(template);
            initializer.setIdentityStableId(30);
            var command = new CreateGameObjectChildCommand(fixture.world, fixture.history.historyIds(),
                    fixture.identities, parent, initializer, null);
            fixture.history.execute(command);
            var representations = fixture.world.getMapper(games.pixscape.runtime.component.spatial.SpatialRepresentationComponent.class);
            for (int cycle = 0; cycle < 3; cycle++) {
                fixture.process();
                int child = command.getCreatedEntityId();
                assertTrue(representations.has(child));
                assertEquals(1, representations.get(child).rectangles.staticRectangle.width, 0);
                assertEquals(0, representations.get(child).rectangles.staticRectangle.pivotX, 0);
                assertFalse(fixture.world.getMapper(games.pixscape.runtime.component.spatial.SpatialFootprintComponent.class).has(child));
                assertFalse(representations.has(parent));
                fixture.history.undo();
                fixture.process();
                assertFalse(fixture.history.canUndo());
                fixture.history.redo();
            }
        } finally {
            fixture.close();
        }
    }

    @Test
    public void addingSpatialInitializesNestedVisualsAndPreservesExistingSettings() {
        Fixture fixture = new Fixture();
        try {
            int layer = fixture.world.create();
            fixture.world.getMapper(LayerComponent.class).create(layer).spatialEnabled = true;
            int root = fixture.root(10, 0, 0);
            int nested = fixture.root(11, 0, 0);
            fixture.world.getMapper(GameObjectMemberComponent.class).create(nested).parentStableId = 10;
            int visual = fixture.core(20, 0, 0);
            fixture.world.getMapper(TextureRegionComponent.class).create(visual);
            fixture.world.getMapper(GameObjectMemberComponent.class).create(visual).parentStableId = 11;
            int configured = fixture.core(21, 0, 1);
            fixture.world.getMapper(TextureRegionComponent.class).create(configured);
            fixture.world.getMapper(GameObjectMemberComponent.class).create(configured).parentStableId = 10;
            var representations = fixture.world.getMapper(games.pixscape.runtime.component.spatial.SpatialRepresentationComponent.class);
            var authored = new games.pixscape.runtime.spatial.ActorSpatialRectangleData();
            authored.width = .3f;
            authored.pivotX = 12;
            representations.create(configured).rectangles = new games.pixscape.runtime.spatial.ActorSpatialRectangles();
            representations.get(configured).rectangles.put(0, null, authored);
            fixture.process();
            fixture.history.execute(new ToggleSpatialActorCommand(fixture.world, fixture.history.historyIds(),
                    root, true, true, new games.pixscape.runtime.component.spatial.SpatialFootprintComponent()));
            for (int cycle = 0; cycle < 3; cycle++) {
                assertTrue(representations.has(visual));
                assertFalse(representations.has(root));
                assertFalse(representations.has(nested));
                assertSame(authored, representations.get(configured).rectangles.staticRectangle);
                fixture.history.undo();
                assertFalse(representations.has(visual));
                assertSame(authored, representations.get(configured).rectangles.staticRectangle);
                assertFalse(fixture.history.canUndo());
                fixture.history.redo();
            }
        } finally {
            fixture.close();
        }
    }

    @Test
    public void compositionDoesNotReplaceSpriteAnimationOrNeutralIdentity() {
        for (EntityKind kind : new EntityKind[]{EntityKind.SPRITE, EntityKind.ANIMATION, EntityKind.GAME_OBJECT}) {
            Fixture fixture = new Fixture();
            try {
                int parent = kind == EntityKind.GAME_OBJECT ? fixture.root(10, 0, 0) : fixture.core(10, 0, 0);
                fixture.world.getMapper(EntityMetaComponent.class).create(parent).kind = kind;
                if (kind != EntityKind.GAME_OBJECT) {
                    fixture.world.getMapper(TextureRegionComponent.class).create(parent);
                    fixture.world.getMapper(VisibilityComponent.class).create(parent).visible = false;
                }
                if (kind == EntityKind.ANIMATION) {
                    fixture.world.getMapper(AnimationComponent.class).create(parent);
                }
                fixture.process();
                TestChildInitializer initializer = new TestChildInitializer(fixture.world, EntityKind.SPRITE);
                initializer.setIdentityStableId(30);
                CreateGameObjectChildCommand command = new CreateGameObjectChildCommand(fixture.world,
                        fixture.history.historyIds(), fixture.identities, parent, initializer, null);
                fixture.history.execute(command);
                for (int cycle = 0; cycle < 3; cycle++) {
                    fixture.process();
                    assertEquals(kind, fixture.world.getMapper(EntityMetaComponent.class).get(parent).kind);
                    assertTrue(fixture.world.getMapper(GameObjectComponent.class).has(parent));
                    if (kind != EntityKind.GAME_OBJECT) {
                        assertFalse(fixture.world.getMapper(VisibilityComponent.class).get(parent).visible);
                    }
                    fixture.history.undo();
                    fixture.process();
                    assertEquals(kind, fixture.world.getMapper(EntityMetaComponent.class).get(parent).kind);
                    assertEquals(kind == EntityKind.GAME_OBJECT,
                            fixture.world.getMapper(GameObjectComponent.class).has(parent));
                    fixture.history.redo();
                }
            } finally {
                fixture.close();
            }
        }
    }

    @Test
    public void addingToOrdinarySpritePromotesInOneUndoWithoutMovingItsPixels() {
        Fixture fixture = new Fixture();
        try {
            int top = fixture.root(10, 2, 0);
            fixture.setTransform(top, 100, -40, .7f, 2, 2);
            int sprite = fixture.core(11, 2, 1);
            fixture.world.getMapper(TextureRegionComponent.class).create(sprite);
            fixture.world.getMapper(GameObjectMemberComponent.class).create(sprite).parentStableId = 10;
            fixture.setTransform(sprite, 17, -5, .4f, 1.5f, 1.5f);
            fixture.transform(sprite).originX = 13;
            fixture.transform(sprite).originY = 7;
            fixture.process();
            TransformComponent before = copy(fixture.transform(sprite));
            TransformComponent worldBefore = GameObjectTransformMath.localToWorld(
                    fixture.transform(top), before, false, new TransformComponent());
            TestChildInitializer initializer = new TestChildInitializer(fixture.world, EntityKind.SPRITE);
            initializer.setIdentityStableId(30);
            CreateGameObjectChildCommand command = new CreateGameObjectChildCommand(
                    fixture.world, fixture.history.historyIds(), fixture.identities, sprite, initializer, null);
            fixture.history.execute(command);
            fixture.process();
            for (int cycle = 0; cycle < 3; cycle++) {
                assertTrue(fixture.world.getMapper(GameObjectComponent.class).has(sprite));
                TransformComponent worldAfter = GameObjectTransformMath.localToWorld(
                        fixture.transform(top), fixture.transform(sprite), true, new TransformComponent());
                assertArrayEquals(visualBounds(worldBefore, 40, 24, null), visualBounds(worldAfter, 40, 24, null), .0001f);
                assertEquals(0, fixture.transform(command.getCreatedEntityId()).x, 0);
                fixture.history.undo();
                fixture.process();
                assertFalse(fixture.world.getMapper(GameObjectComponent.class).has(sprite));
                assertTransformEquals(before, fixture.transform(sprite));
                assertFalse(fixture.history.canUndo());
                fixture.history.redo();
                fixture.process();
            }
            fixture.transform(sprite).scaleX = -1;
            assertNotNull(CreateGameObjectChildCommand.parentRejection(fixture.world, sprite));
            assertEquals(-1, fixture.transform(sprite).scaleX, 0);
        } finally {
            fixture.close();
        }
    }

    @Test
    public void createsSpriteChild() {
        assertCreates(EntityKind.SPRITE);
    }

    @Test
    public void createsAnimationChild() {
        assertCreates(EntityKind.ANIMATION);
    }

    @Test
    public void createsPointLightChild() {
        assertCreates(EntityKind.POINT_LIGHT);
    }

    @Test
    public void createsConeLightChild() {
        assertCreates(EntityKind.CONE_LIGHT);
    }

    @Test
    public void createsNestedGameObjectChild() {
        assertCreates(EntityKind.GAME_OBJECT);
    }

    @Test
    public void firstVisualChildKeepsParentPoseAndUsesLocalOrigin() {
        for (EntityKind kind : new EntityKind[]{
                EntityKind.SPRITE,
                EntityKind.ANIMATION,
                EntityKind.POINT_LIGHT,
                EntityKind.CONE_LIGHT}) {
            assertFirstVisualChildKeepsParentPose(kind);
        }
    }

    @Test
    public void nestedFirstVisualChildKeepsBothParentFrames() {
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
            TransformComponent nestedBefore = copy(fixture.transform(nested));

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
            assertTransformEquals(nestedBefore, nestedLocal);
            TransformComponent child = fixture.transform(command.getCreatedEntityId());
            assertEquals(0, child.x, 0);
            assertEquals(0, child.y, 0);
            assertEquals(0, child.rotationRad, 0);
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

    private static void assertFirstVisualChildKeepsParentPose(EntityKind kind) {
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
            CreateGameObjectChildCommand command = new CreateGameObjectChildCommand(
                    fixture.world, fixture.history.historyIds(), fixture.identities,
                    parent, initializer, null);

            fixture.history.execute(command);
            fixture.process();
            TransformComponent rootAfter = copy(fixture.transform(parent));
            TransformComponent childLocalAfter = copy(
                    fixture.transform(command.getCreatedEntityId()));
            assertTransformEquals(rootBefore, rootAfter);
            assertEquals(0, childLocalAfter.x, 0);
            assertEquals(0, childLocalAfter.y, 0);
            assertEquals(0, childLocalAfter.rotationRad, 0);
            TransformComponent reconstructed = GameObjectTransformMath.localToWorld(
                    rootAfter, childLocalAfter, new TransformComponent());
            TransformComponent expectedWorld = copy(reconstructed);

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
                assertTransformEquals(expectedWorld, reconstructed);
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
                default -> {
                }
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
