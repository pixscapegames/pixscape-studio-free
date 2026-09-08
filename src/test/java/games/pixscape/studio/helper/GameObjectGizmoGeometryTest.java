package games.pixscape.studio.helper;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.AABBComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.hierarchy.GameObjectCompositionState;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyFlushSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.GameObjectCompositionSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.runtime.system.UpdateWorldGeometrySystem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameObjectGizmoGeometryTest {

    @Test
    public void selectedRootLocalBoundsStayStableWhileWorldAabbAndOrientedQuadRotate() {
        Fixture f = new Fixture();
        int root = f.root(10, -1);
        int child = f.drawable(11, 10, 100f, 60f);
        TransformComponent rootTransform = f.transforms.get(root);
        rootTransform.x = 10f;
        rootTransform.y = 20f;
        rootTransform.originX = 50f;
        rootTransform.originY = 30f;
        TransformComponent childTransform = f.transforms.get(child);
        float childX = childTransform.x;
        float childY = childTransform.y;
        f.process();

        GameObjectGizmoGeometry geometry = new GameObjectGizmoGeometry(f.world);
        float[] local = new float[4];
        float[] corners = new float[8];
        assertTrue(geometry.writeLocalBounds(root, local));
        assertBounds(local, 0f, 0f, 100f, 60f);
        assertTrue(geometry.writeWorldCorners(root, 8f, corners));
        assertEdge(corners, 100f, 60f, 0f);
        GameObjectCompositionState composition = f.composition.state();
        float initialAabbWidth = composition.maxX[root] - composition.minX[root];

        rootTransform.rotationRad = (float) Math.toRadians(30d);
        f.dirty.markDirty().transform(root).rotation().commit();
        f.process();

        assertTrue(geometry.writeLocalBounds(root, local));
        assertBounds(local, 0f, 0f, 100f, 60f);
        assertTrue(geometry.writeWorldCorners(root, 8f, corners));
        assertEdge(corners, 100f, 60f, 30f);
        assertTrue(composition.maxX[root] - composition.minX[root] > initialAabbWidth);

        rootTransform.rotationRad = (float) (Math.PI * 0.5d);
        rootTransform.x += 15f;
        rootTransform.y -= 4f;
        rootTransform.scaleX = 2f;
        rootTransform.scaleY = 2f;
        f.dirty.markDirty().transform(root).position().rotation().scale().commit();
        f.process();

        assertTrue(geometry.writeLocalBounds(root, local));
        assertBounds(local, 0f, 0f, 100f, 60f);
        assertTrue(geometry.writeWorldCorners(root, 8f, corners));
        assertEdge(corners, 200f, 120f, 90f);
        assertEquals(childX, childTransform.x, 0f);
        assertEquals(childY, childTransform.y, 0f);
    }

    @Test
    public void childEditChangesLocalBoundsWithoutMutatingTheRoot() {
        Fixture f = new Fixture();
        int root = f.root(10, -1);
        int child = f.drawable(11, 10, 40f, 20f);
        f.process();
        GameObjectGizmoGeometry geometry = new GameObjectGizmoGeometry(f.world);
        float[] local = new float[4];
        assertTrue(geometry.writeLocalBounds(root, local));
        assertBounds(local, 0f, 0f, 40f, 20f);

        f.transforms.get(child).x = 12f;
        f.dirty.markDirty().transform(child).position().commit();
        f.process();

        assertTrue(geometry.writeLocalBounds(root, local));
        assertBounds(local, 12f, 0f, 52f, 20f);
        assertEquals(0f, f.transforms.get(root).x, 0f);
        assertEquals(0f, f.transforms.get(root).rotationRad, 0f);
    }

    @Test
    public void nestedSelectionUsesItsResolvedWorldFrameAndInvisibleLeavesAreExcluded() {
        Fixture f = new Fixture();
        int outer = f.root(10, -1);
        int nested = f.root(11, 10);
        int leaf = f.drawable(12, 11, 40f, 20f);
        f.transforms.get(outer).rotationRad = (float) Math.toRadians(20d);
        f.transforms.get(nested).rotationRad = (float) Math.toRadians(30d);
        f.process();

        GameObjectGizmoGeometry geometry = new GameObjectGizmoGeometry(f.world);
        float[] local = new float[4];
        float[] corners = new float[8];
        assertTrue(geometry.writeLocalBounds(nested, local));
        assertBounds(local, 0f, 0f, 40f, 20f);
        assertTrue(geometry.writeWorldCorners(nested, 8f, corners));
        assertEdge(corners, 40f, 20f, 50f);
        assertTrue(geometry.writeLocalBounds(outer, local));

        int slot = f.renderState.renderSlotForEntity(leaf);
        f.renderState.enabled[slot] = false;
        f.process();
        assertFalse(geometry.writeLocalBounds(nested, local));
        assertTrue(geometry.writeWorldCorners(nested, 8f, corners));
        assertEdge(corners, 16f, 16f, 50f);
        assertFalse(f.world.getMapper(DimensionsComponent.class).has(nested));
        assertFalse(f.world.getMapper(AABBComponent.class).has(nested));
        assertFalse(f.world.getMapper(OrientedBoundsComponent.class).has(nested));
    }

    private static void assertBounds(float[] bounds, float minX, float minY, float maxX, float maxY) {
        assertEquals(minX, bounds[0], 0.02f);
        assertEquals(minY, bounds[1], 0.02f);
        assertEquals(maxX, bounds[2], 0.02f);
        assertEquals(maxY, bounds[3], 0.02f);
    }

    private static void assertEdge(float[] corners, float width, float height, float angleDegrees) {
        float dx = corners[2] - corners[0];
        float dy = corners[3] - corners[1];
        float hx = corners[6] - corners[0];
        float hy = corners[7] - corners[1];
        assertEquals(width, (float) Math.sqrt(dx * dx + dy * dy), 0.02f);
        assertEquals(height, (float) Math.sqrt(hx * hx + hy * hy), 0.02f);
        assertEquals(angleDegrees, (float) Math.toDegrees(Math.atan2(dy, dx)), 0.02f);
    }

    private static final class Fixture {
        final DynamicEntityRenderState renderState = new DynamicEntityRenderState(32);
        final DirtyTrackerSystem dirty = new DirtyTrackerSystem(32);
        final GameObjectHierarchySystem hierarchy = new GameObjectHierarchySystem(32);
        final GameObjectCompositionSystem composition = new GameObjectCompositionSystem(renderState, 32);
        final World world = new World(new WorldConfigurationBuilder()
                .with(dirty, hierarchy, new UpdateWorldGeometrySystem(), composition,
                        new DirtyFlushSystem())
                .build());
        final com.artemis.ComponentMapper<TransformComponent> transforms =
                world.getMapper(TransformComponent.class);
        final IdentityRegistry identities = new IdentityRegistry();

        Fixture() {
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.nextEntityStableId = 100;
            identities.bind(world, meta);
        }

        int root(int stableId, int parentStableId) {
            int entity = base(stableId, parentStableId);
            world.getMapper(GameObjectComponent.class).create(entity);
            return entity;
        }

        int drawable(int stableId, int parentStableId, float width, float height) {
            int entity = base(stableId, parentStableId);
            DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).create(entity);
            dimensions.width = width;
            dimensions.height = height;
            world.getMapper(OrientedBoundsComponent.class).create(entity);
            world.getMapper(AABBComponent.class).create(entity);
            int slot = renderState.acquireSlotForEntity(entity);
            renderState.enabled[slot] = true;
            renderState.visible[slot] = true;
            return entity;
        }

        int base(int stableId, int parentStableId) {
            int entity = world.create();
            world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = stableId;
            transforms.create(entity);
            world.getMapper(EntityIndexComponent.class).create(entity);
            if (parentStableId >= 0) {
                world.getMapper(GameObjectMemberComponent.class).create(entity)
                        .parentStableId = parentStableId;
            }
            return entity;
        }

        void process() {
            world.process();
        }
    }
}
