package games.pixscape.studio.service;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.system.*;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.tiled.TiledAllocatorService;
import games.pixscape.studio.service.tiled.TiledToolService;
import games.pixscape.studio.system.UiRefreshDispatchSystem;
import games.pixscape.studio.ui.main.WorldCanvas;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

public class AlignServiceTest {
    private static final float EPS = 0.03f;

    @Test
    public void zeroOneAndSeveralTargetsShareAdmission() throws Exception {
        try (Fixture f = new Fixture()) {
            assertFalse(f.align.isPlacementAvailable());
            int a = f.sprite(10, 0, 20, 10), b = f.sprite(40, 0, 8, 10);
            f.select(a);
            assertFalse(f.align.isPlacementAvailable());
            f.align.alignLeft();
            assertFalse(f.history.canUndo());
            f.select(a, b);
            assertTrue(f.align.isPlacementAvailable());
            f.align.alignLeft();
            assertEquals(10, f.t.get(a).x, EPS);
            assertEquals(10, f.t.get(b).x, EPS);
        }
    }

    @Test
    public void everyAlignmentPreservesTheFirstSelectedReferenceForEachSupportedType() throws Exception {
        for (int type = 0; type < 3; type++) {
            for (AlignService.Operation op : AlignService.Operation.values()) {
                if (op == AlignService.Operation.PACK_HORIZONTAL || op == AlignService.Operation.PACK_VERTICAL)
                    continue;
                try (Fixture f = new Fixture()) {
                    int a = f.target(type, 10, 15, 20, 30);
                    int b = f.target(type, 90, 75, 8, 12);
                    f.select(a, b);
                    f.align.execute(op);
                    f.process();
                    assertEquals(10, f.t.get(a).x, EPS);
                    assertEquals(15, f.t.get(a).y, EPS);
                    assertEquals(anchor(f.bounds(a), op), anchor(f.bounds(b), op), EPS);
                }
            }
        }
    }

    @Test
    public void mixedPlacementMovesOnlyRootsAndUndoRedoIsOneAction() throws Exception {
        try (Fixture f = new Fixture()) {
            int image = f.sprite(10, 15, 20, 30);
            int animation = f.target(1, 90, 75, 8, 12);
            int root = f.root(150, 25);
            int child = f.sprite(5, 7, 18, 23);
            f.attach(child, root);
            f.select(image, animation, root);
            f.align.alignLeft();
            f.process();
            assertEquals(1, f.history.getCursor());
            assertEquals(f.bounds(image)[0], f.bounds(animation)[0], EPS);
            assertEquals(f.bounds(image)[0], f.bounds(root)[0], EPS);
            assertEquals(5, f.t.get(child).x, 0);
            assertEquals(7, f.t.get(child).y, 0);
            for (int i = 0; i < 3; i++) {
                f.history.undo();
                f.process();
                assertEquals(90, f.t.get(animation).x, EPS);
                assertEquals(150, f.t.get(root).x, EPS);
                f.history.redo();
                f.process();
                assertEquals(f.bounds(image)[0], f.bounds(root)[0], EPS);
                assertEquals(5, f.t.get(child).x, 0);
            }
        }
    }

    @Test
    public void rotatedScaledParentsAndOriginsUseWorldBoundsAndLocalDisplacements() throws Exception {
        try (Fixture f = new Fixture()) {
            int parent = f.root(130, -40);
            TransformComponent p = f.t.get(parent);
            p.originX = 7;
            p.originY = 13;
            p.rotationRad = 0.71f;
            p.scaleX = p.scaleY = 2;
            int nested = f.root(30, 25);
            f.t.get(nested).rotationRad = -0.35f;
            f.t.get(nested).originX = 8;
            f.attach(nested, parent);
            int image = f.sprite(23, 41, 24, 13);
            f.attach(image, nested);
            TransformComponent t = f.t.get(image);
            t.originX = 3;
            t.originY = 8;
            t.rotationRad = -0.42f;
            t.scaleX = -1.5f;
            t.scaleY = 0.7f;
            int reference = f.target(1, -45, 75, 32, 26);
            f.select(reference, image);
            float beforeX = t.x, beforeY = t.y;
            float bottom = f.bounds(image)[1];
            f.align.alignRight();
            f.process();
            assertEquals(f.bounds(reference)[2], f.bounds(image)[2], EPS);
            assertEquals(bottom, f.bounds(image)[1], EPS);
            assertNotEquals(beforeX, t.x, EPS);
            assertNotEquals(beforeY, t.y, EPS);
            assertEquals(130, p.x, 0);
            assertEquals(30, f.t.get(nested).x, 0);
            f.history.undo();
            f.process();
            assertEquals(beforeX, t.x, EPS);
            assertEquals(beforeY, t.y, EPS);
        }
    }

    @Test
    public void rotatedCompositionUsesExactWorldUnionInsteadOfRotatedGizmoRectangle() throws Exception {
        try (Fixture f = new Fixture()) {
            int root = f.root(100, 80);
            f.t.get(root).rotationRad = 0.65f;
            int a = f.sprite(0, 0, 10, 10), b = f.sprite(50, 50, 10, 10);
            f.attach(a, root);
            f.attach(b, root);
            int reference = f.sprite(-20, 0, 25, 20);
            f.select(reference, root);
            f.align.alignRight();
            f.process();
            assertEquals(f.bounds(reference)[2], f.bounds(root)[2], EPS);
            assertEquals(0, f.t.get(a).x, 0);
            assertEquals(50, f.t.get(b).x, 0);
        }
    }

    @Test
    public void rootAndDescendantNormalizeCountAndKeepFirstCompositionAsReference() throws Exception {
        try (Fixture f = new Fixture()) {
            int root = f.root(40, 20), child = f.sprite(5, 0, 15, 10);
            f.attach(child, root);
            f.select(child, root);
            assertFalse(f.align.isPlacementAvailable());
            f.align.packHorizontal();
            assertFalse(f.history.canUndo());
            int other = f.sprite(-30, -40, 8, 10);
            f.select(child, other, root);
            assertTrue(f.align.isPlacementAvailable());
            f.align.alignLeft();
            f.process();
            assertEquals(40, f.t.get(root).x, 0);
            assertEquals(5, f.t.get(child).x, 0);
            assertEquals(45, f.t.get(other).x, EPS);
        }
    }

    @Test
    public void packingKeepsPositionOrderAndLowestEdgeRatherThanSelectionReference() throws Exception {
        for (boolean horizontal : new boolean[]{true, false}) {
            try (Fixture f = new Fixture()) {
                int a = f.target(0, 0, 0, 10, 4);
                int b = f.target(1, 20, 20, 6, 3);
                int c = f.target(2, 40, 40, 5, 2);
                f.select(c, a, b);
                f.align.execute(horizontal ? AlignService.Operation.PACK_HORIZONTAL : AlignService.Operation.PACK_VERTICAL);
                f.process();
                int low = horizontal ? 0 : 1, high = horizontal ? 2 : 3;
                assertEquals(0, f.bounds(a)[low], EPS);
                assertEquals(f.bounds(a)[high], f.bounds(b)[low], EPS);
                assertEquals(f.bounds(b)[high], f.bounds(c)[low], EPS);
                assertEquals(1, f.history.getCursor());
            }
        }
    }

    @Test
    public void unavailableBoundsAndIncompatibleSelectionRejectWholeOperation() throws Exception {
        for (int invalid = 0; invalid < 5; invalid++) {
            try (Fixture f = new Fixture()) {
                int a = f.sprite(10, 0, 10, 10), b = f.sprite(50, 0, 10, 10);
                int c = invalid == 0 ? f.root(100, 0) : f.sprite(100, 0, 10, 10);
                if (invalid == 1) f.world.getMapper(ParticleEmitterComponent.class).create(c);
                if (invalid == 2) f.world.getMapper(DimensionsComponent.class).remove(c);
                if (invalid == 3) f.t.get(c).scaleX = Float.NaN;
                if (invalid == 4) f.world.getMapper(TextureRegionComponent.class).remove(c);
                // Invalid authored geometry is tested at admission, before Runtime validation.
                f.selection.selectOnly(a);
                f.selection.selectAdd(b);
                f.selection.selectAdd(c);
                assertFalse(f.align.isPlacementAvailable());
                for (AlignService.Operation op : AlignService.Operation.values()) f.align.execute(op);
                assertEquals(50, f.t.get(b).x, 0);
                assertEquals(0, f.history.getCursor());
            }
        }
    }

    @Test
    public void hiddenOnlyGameObjectHasNoContentBoundsEvenThoughGizmoHasFallback() throws Exception {
        try (Fixture f = new Fixture()) {
            int root = f.root(100, 0), child = f.sprite(5, 0, 10, 10), other = f.sprite(0, 0, 10, 10);
            f.attach(child, root);
            f.world.getMapper(VisibilityComponent.class).create(child).visible = false;
            f.select(root, other);
            assertFalse(f.align.isPlacementAvailable());
            f.align.alignLeft();
            assertFalse(f.history.canUndo());
        }
    }

    @Test
    public void directCommandsRejectIncompatibleModesAndDetachedDocuments() throws Exception {
        try (Fixture f = new Fixture()) {
            int a = f.sprite(10, 0, 10, 10), b = f.sprite(50, 0, 10, 10);
            f.select(a, b);
            for (StudioEditingMode mode : new StudioEditingMode[]{StudioEditingMode.TILED,
                    StudioEditingMode.SPATIAL, StudioEditingMode.PHYSICS, StudioEditingMode.HUD}) {
                if (mode == StudioEditingMode.HUD) f.modes.activateHudDocument(0);
                else f.modes.activateSceneDocument(mode, 0);
                assertFalse(f.align.isPlacementAvailable());
                for (AlignService.Operation op : AlignService.Operation.values()) f.align.execute(op);
                assertEquals(50, f.t.get(b).x, 0);
            }
            f.modes.activateSceneDocument(StudioEditingMode.NORMAL, 0);
            assertTrue(f.align.isPlacementAvailable());
            f.context.detached();
            assertFalse(f.align.isPlacementAvailable());
            f.align.alignLeft();
            assertFalse(f.history.canUndo());
        }
    }

    @Test
    public void physicsRootMovementPreservesUnitScaleAndLocalBodyTransform() throws Exception {
        try (Fixture f = new Fixture()) {
            int root = f.root(100, 0), body = f.sprite(5, 0, 10, 10), reference = f.sprite(0, 0, 10, 10);
            f.attach(body, root);
            f.world.getMapper(PhysicsBodyComponent.class).create(body);
            f.select(reference, root);
            assertTrue(f.align.isPlacementAvailable());
            f.align.alignLeft();
            f.process();
            assertEquals(5, f.t.get(body).x, 0);
            assertEquals(1, f.t.get(root).scaleX, 0);
            f.t.get(root).scaleX = f.t.get(root).scaleY = 2;
            assertFalse(f.align.isPlacementAvailable());
        }
    }

    @Test
    public void consecutiveCommandsReadCurrentAuthoredTransformsBeforeAnotherEcsStep() throws Exception {
        try (Fixture f = new Fixture()) {
            int a = f.target(2, 10, 10, 20, 20), b = f.target(2, 70, 80, 10, 10);
            f.select(a, b);
            f.align.centerHorizontal();
            f.align.centerVertical();
            f.process();
            assertEquals(15, f.t.get(b).x, EPS);
            assertEquals(15, f.t.get(b).y, EPS);
            f.history.undo();
            f.history.undo();
            f.align.alignRight();
            f.process();
            assertEquals(f.bounds(a)[2], f.bounds(b)[2], EPS);
        }
    }

    @Test
    public void nestedGameObjectMovementConvertsDeltaWithoutEditingDescendants() throws Exception {
        try (Fixture f = new Fixture()) {
            int parent = f.root(30, 40), nested = f.root(20, 35), child = f.sprite(5, 7, 20, 10);
            f.attach(nested, parent);
            f.attach(child, nested);
            f.t.get(parent).rotationRad = 0.8f;
            f.t.get(parent).scaleX = f.t.get(parent).scaleY = 1.5f;
            f.t.get(nested).rotationRad = -0.3f;
            f.t.get(nested).originY = 5;
            int reference = f.sprite(-50, -60, 20, 40);
            f.select(reference, nested);
            float left = f.bounds(nested)[0];
            f.align.alignBottom();
            f.process();
            assertEquals(f.bounds(reference)[1], f.bounds(nested)[1], EPS);
            assertEquals(left, f.bounds(nested)[0], EPS);
            assertEquals(5, f.t.get(child).x, 0);
            assertEquals(7, f.t.get(child).y, 0);
            assertEquals(30, f.t.get(parent).x, 0);
            assertEquals(40, f.t.get(parent).y, 0);
        }
    }

    @Test
    public void deformedImageAndCompositionUseTheRenderedCorners() throws Exception {
        try (Fixture f = new Fixture()) {
            int root = f.root(100, 0), child = f.sprite(5, 7, 20, 10);
            f.attach(child, root);
            f.world.getMapper(QuadDeformComponent.class).create(child).trX = 35;
            f.t.get(root).rotationRad = 0.5f;
            int image = f.sprite(-50, -60, 20, 40);
            f.world.getMapper(QuadDeformComponent.class).create(image).blY = -20;
            f.select(image, root);
            f.align.alignRight();
            f.process();
            assertEquals(f.bounds(image)[2], f.bounds(root)[2], EPS);
            f.align.alignBottom();
            f.process();
            assertEquals(f.bounds(image)[1], f.bounds(root)[1], EPS);
        }
    }

    @Test
    public void hiddenPhysicsAndInvalidHierarchyCannotBypassAdmission() throws Exception {
        try (Fixture f = new Fixture()) {
            int parent = f.root(100, 0), nested = f.root(0, 0);
            int body = f.sprite(5, 0, 10, 10), visible = f.sprite(0, 0, 10, 10);
            int reference = f.sprite(0, 0, 10, 10);
            f.attach(nested, parent);
            f.attach(body, nested);
            f.attach(visible, parent);
            f.world.getMapper(PhysicsBodyComponent.class).create(body);
            f.world.getMapper(VisibilityComponent.class).create(nested).visible = false;
            f.select(reference, parent);
            assertTrue(f.align.isPlacementAvailable());
            // Product is one, but each Physics ancestor must individually have unit scale.
            f.t.get(parent).scaleX = f.t.get(parent).scaleY = 2;
            f.t.get(nested).scaleX = f.t.get(nested).scaleY = 0.5f;
            assertFalse(f.align.isPlacementAvailable());
            f.align.alignLeft();
            assertFalse(f.history.canUndo());
            f.t.get(parent).scaleX = f.t.get(parent).scaleY = 1;
            f.t.get(nested).scaleX = f.t.get(nested).scaleY = 1;
            f.world.getMapper(GameObjectMemberComponent.class).get(nested).parentStableId = Integer.MAX_VALUE;
            assertFalse(f.align.isPlacementAvailable());
        }
    }

    @Test
    public void packingUsesEntityIdToBreakPositionTies() throws Exception {
        try (Fixture f = new Fixture()) {
            int first = f.sprite(10, 0, 8, 10), second = f.sprite(10, 0, 15, 10);
            f.select(second, first);
            f.align.packHorizontal();
            f.process();
            assertEquals(10, f.t.get(first).x, EPS);
            assertEquals(18, f.t.get(second).x, EPS);
        }
    }

    private static float anchor(float[] b, AlignService.Operation op) {
        return switch (op) {
            case LEFT -> b[0];
            case RIGHT -> b[2];
            case TOP -> b[3];
            case BOTTOM -> b[1];
            case CENTER_HORIZONTAL -> (b[0] + b[2]) * 0.5f;
            case CENTER_VERTICAL -> (b[1] + b[3]) * 0.5f;
            default -> throw new AssertionError(op);
        };
    }

    /**
     * Real hierarchy/geometry/history services; only the GL canvas shell is uninitialized.
     */
    public static final class Fixture implements AutoCloseable {
        public final DynamicEntityRenderState render = new DynamicEntityRenderState(32);
        public final DirtyTrackerSystem dirty = new DirtyTrackerSystem(32);
        public final GameObjectHierarchySystem hierarchy = new GameObjectHierarchySystem(32);
        public final GameObjectCompositionSystem composition = new GameObjectCompositionSystem(render, 32);
        public final World world;
        public final StudioEditingModeService modes = new StudioEditingModeService();
        public final SceneEditorContext context = new SceneEditorContext("placement-test", modes);
        public final SelectionService selection;
        public final HistoryManager history;
        public final ComponentMapper<TransformComponent> t;
        private final boolean renderSync;
        public final WorldCanvas canvas;
        public final AlignService align;

        public Fixture() throws Exception {
            this(false);
        }

        public Fixture(boolean renderSync) throws Exception {
            this.renderSync = renderSync;
            WorldConfigurationBuilder builder = new WorldConfigurationBuilder().with(dirty, hierarchy,
                    new UpdateWorldGeometrySystem());
            if (renderSync) builder.with(new RenderSpriteSyncSystem(render));
            world = new World(builder.with(composition, new UiRefreshDispatchSystem(), new DirtyFlushSystem()).build());
            selection = context.initializeWorld(world, new TiledAllocatorService(), new SceneMeta());
            history = context.historyManager();
            t = world.getMapper(TransformComponent.class);
            canvas = allocate(WorldCanvas.class);
            setField(canvas, "sceneEditorContext", context);
            setField(canvas, "dynamicEntityState", render);
            setField(canvas, "studioEditingModeService", modes);
            setField(canvas, "tiledToolService", new TiledToolService());
            context.layerService().addLayerTop("Placement");
            world.process();
            context.attached();
            modes.activateSceneDocument(StudioEditingMode.NORMAL, 0);
            align = new AlignService(canvas);
            setField(canvas, "alignService", align);
        }

        public int root(float x, float y) {
            int e = base(x, y);
            world.getMapper(GameObjectComponent.class).create(e);
            return e;
        }

        public int sprite(float x, float y, float w, float h) {
            int e = base(x, y);
            DimensionsComponent d = world.getMapper(DimensionsComponent.class).create(e);
            d.width = w;
            d.height = h;
            world.getMapper(TextureRegionComponent.class).create(e).valid = true;
            world.getMapper(RenderMaterialComponent.class).create(e).textureHandle = 1;
            world.getMapper(VisibilityComponent.class).create(e);
            world.getMapper(OrientedBoundsComponent.class).create(e);
            world.getMapper(AABBComponent.class).create(e);
            if (!renderSync) {
                int slot = render.acquireSlotForEntity(e);
                render.enabled[slot] = render.visible[slot] = true;
            }
            return e;
        }

        public int target(int type, float x, float y, float w, float h) {
            if (type == 2) {
                int root = root(x, y);
                attach(sprite(0, 0, w, h), root);
                return root;
            }
            int e = sprite(x, y, w, h);
            if (type == 1) world.getMapper(AnimationComponent.class).create(e);
            return e;
        }

        private int base(float x, float y) {
            int e = world.create();
            t.create(e).x = x;
            t.get(e).y = y;
            world.getMapper(EntityIndexComponent.class).create(e);
            context.identityRegistry().ensureStableId(e);
            return e;
        }

        public void attach(int child, int root) {
            world.getMapper(GameObjectMemberComponent.class).create(child).parentStableId =
                    context.identityRegistry().getStableId(root);
        }

        public void select(int... entities) {
            selection.clearSelection();
            for (int e : entities) selection.selectAdd(e);
            process();
        }

        public void process() {
            EventFlow.i().flush();
            world.process();
        }

        public float[] bounds(int e) {
            if (world.getMapper(GameObjectComponent.class).has(e)) {
                var s = composition.state();
                assertTrue(s.boundsResolved[e]);
                return new float[]{s.minX[e], s.minY[e], s.maxX[e], s.maxY[e]};
            }
            AABBComponent b = world.getMapper(AABBComponent.class).get(e);
            return new float[]{b.minX, b.minY, b.maxX, b.maxY};
        }

        public void changeWidth(int e, float width) {
            DimensionsComponent d = world.getMapper(DimensionsComponent.class).get(e);
            float before = d.width;
            history.execute(new Command() {
                public String label() {
                    return "Change width";
                }

                public void redo() {
                    apply(width);
                }

                public void undo() {
                    apply(before);
                }

                private void apply(float value) {
                    d.width = value;
                    dirty.geometry(e, GeometryDirty.ALL);
                }
            });
        }

        @Override
        public void close() {
            context.dispose();
            EventFlow.i().flush();
        }
    }

    public static <T> T allocate(Class<T> type) throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) f.get(null)).allocateInstance(type));
    }

    public static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
