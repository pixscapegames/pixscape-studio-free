package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.component.PrefabInstanceComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.zorder.LayerLogicalOrderService;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ReorderLogicalLayerCommandTest {
    @Test
    public void originalInterleavingNormalizesToOneContiguousPrefabBlock() {
        Harness h = new Harness();
        try {
            int a = h.member(14, 1, "Castle");
            int b = h.member(13, 1, "Castle");
            int c = h.member(10, 1, "Castle");
            int d = h.member(9, 1, "Castle");
            int standalone = h.entity(12);
            h.process();

            h.normalize();

            h.assertZ(a, 4); h.assertZ(b, 3); h.assertZ(c, 2); h.assertZ(d, 1);
            h.assertZ(standalone, 0);
            h.assertSequential();
            assertTrue(h.dirty().isDirty(c, DirtyBits.ORDER));
            assertFalse(h.dirty().isDirty(c, DirtyBits.MATERIAL));

            h.history.undo();
            h.assertZ(a, 14); h.assertZ(b, 13); h.assertZ(c, 10); h.assertZ(d, 9);
            h.assertZ(standalone, 12);
            h.history.redo();
            h.assertZ(a, 4); h.assertZ(standalone, 0);
        } finally {
            h.dispose();
        }
    }

    @Test
    public void compactEntireGappedLayerPreservesDeterministicLogicalOrder() {
        Harness h = new Harness();
        try {
            int top = h.entity(100);
            int a1 = h.member(90, 1, "A");
            int a2 = h.member(50, 1, "A");
            int a3 = h.member(10, 1, "A");
            int middle = h.entity(75);
            int b1 = h.member(70, 2, "B");
            int b2 = h.member(69, 2, "B");
            h.process();

            assertEquals(new IntArray(new int[]{top, a1, a2, a3, middle, b1, b2}),
                    h.order().flattenedTopToBottom());
            h.normalize();

            h.assertZ(top, 6);
            h.assertZ(a1, 5); h.assertZ(a2, 4); h.assertZ(a3, 3);
            h.assertZ(middle, 2);
            h.assertZ(b1, 1); h.assertZ(b2, 0);
            h.assertSequential();
        } finally {
            h.dispose();
        }
    }

    @Test
    public void prefabStandaloneAndPrefabSwapAsTopLevelBlocksWithOneHistoryEntry() {
        Harness h = new Harness();
        try {
            int a1 = h.member(5, 1, "A");
            int a2 = h.member(4, 1, "A");
            int standalone = h.entity(3);
            int b1 = h.member(2, 2, "B");
            int b2 = h.member(1, 2, "B");
            int b3 = h.member(0, 2, "B");
            h.process();

            h.execute(h.order().movePrefab(1, 1));
            assertEquals(1, h.history.getCursor());
            assertEquals(new IntArray(new int[]{standalone, a1, a2, b1, b2, b3}),
                    h.order().flattenedTopToBottom());
            h.assertSequential();

            h.history.undo();
            assertEquals(new IntArray(new int[]{a1, a2, standalone, b1, b2, b3}),
                    h.order().flattenedTopToBottom());
            h.history.redo();
            assertEquals(new IntArray(new int[]{standalone, a1, a2, b1, b2, b3}),
                    h.order().flattenedTopToBottom());
        } finally {
            h.dispose();
        }
    }

    @Test
    public void observedTenEntityLayerMovesPrefabOneSiblingPerClick() {
        Harness h = new Harness();
        try {
            int p1 = h.member(9, 41, "Observed");
            int p2 = h.member(8, 41, "Observed");
            int p3 = h.member(7, 41, "Observed");
            int p4 = h.member(6, 41, "Observed");
            int a = h.entity(5);
            int b = h.entity(4);
            int c = h.entity(3);
            int d = h.entity(2);
            int e = h.entity(1);
            int f = h.entity(0);
            h.process();

            h.execute(h.order().movePrefab(41, 1));
            assertEquals(1, h.history.getCursor());
            h.assertZ(a, 9);
            h.assertZ(p1, 8); h.assertZ(p2, 7);
            h.assertZ(p3, 6); h.assertZ(p4, 5);
            h.assertZ(b, 4); h.assertZ(c, 3); h.assertZ(d, 2);
            h.assertZ(e, 1); h.assertZ(f, 0);
            h.assertSequential();

            h.execute(h.order().movePrefab(41, -1));
            assertEquals(2, h.history.getCursor());
            h.assertZ(p1, 9); h.assertZ(p2, 8);
            h.assertZ(p3, 7); h.assertZ(p4, 6);
            h.assertZ(a, 5); h.assertZ(f, 0);
            h.assertSequential();
        } finally {
            h.dispose();
        }
    }

    @Test
    public void standaloneCrossesPrefabAtomicallyAndChildMovesOnlyInsidePrefab() {
        Harness h = new Harness();
        try {
            int standalone = h.entity(3);
            int a = h.member(2, 1, "A");
            int b = h.member(1, 1, "A");
            int c = h.member(0, 1, "A");
            h.process();

            h.execute(h.order().moveEntity(standalone, 1));
            assertEquals(new IntArray(new int[]{a, b, c, standalone}),
                    h.order().flattenedTopToBottom());
            h.assertSequential();

            h.execute(h.order().moveEntity(a, 1));
            assertEquals(new IntArray(new int[]{b, a, c, standalone}),
                    h.order().flattenedTopToBottom());
            assertNull(h.order().moveEntity(b, -1));
            assertNull(h.order().moveEntity(c, 1));

            h.history.undo();
            assertEquals(new IntArray(new int[]{a, b, c, standalone}),
                    h.order().flattenedTopToBottom());
        } finally {
            h.dispose();
        }
    }

    @Test
    public void prefabSwapsWithPrefabAndLockedLayerRejectsWithoutHistory() {
        Harness h = new Harness();
        try {
            int a1 = h.member(4, 1, "A");
            int a2 = h.member(3, 1, "A");
            int b1 = h.member(2, 2, "B");
            int b2 = h.member(1, 2, "B");
            int b3 = h.member(0, 2, "B");
            h.process();

            h.execute(h.order().movePrefab(1, 1));
            assertEquals(new IntArray(new int[]{b1, b2, b3, a1, a2}),
                    h.order().flattenedTopToBottom());
            h.assertSequential();

            h.history.undo();
            h.layerMeta.locked = true;
            ReorderLogicalLayerCommand locked = h.command(h.order().movePrefab(1, 1));
            assertTrue(locked.isNoop());
            h.history.execute(locked);
            assertEquals(0, h.history.getCursor());
        } finally {
            h.dispose();
        }
    }

    @Test
    public void equalZValuesNormalizeDeterministicallyAndHistoryIdsSurviveRecreation() {
        Harness h = new Harness();
        try {
            int a = h.member(12, 1, "A");
            int b = h.member(12, 1, "A");
            int standalone = h.entity(12);
            h.process();

            IntArray deterministic = h.order().flattenedTopToBottom();
            assertEquals(new IntArray(new int[]{a, b, standalone}), deterministic);
            h.normalize();
            h.assertZ(a, 2); h.assertZ(b, 1); h.assertZ(standalone, 0);

            h.history.execute(new DeleteEntitiesCommand(
                    h.world,
                    h.history.historyIds(),
                    new IntArray(new int[]{a, b})));
            h.process();
            h.history.undo();
            h.process();
            h.history.undo();
            h.process();

            IntArray restored = new IntArray();
            for (LayerLogicalOrderService.LogicalItem item : h.order().items()) {
                if (item.isPrefab()) restored.addAll(item.members());
            }
            assertEquals(2, restored.size);
            for (int i = 0; i < restored.size; i++) h.assertZ(restored.get(i), 12);
            h.assertZ(standalone, 12);
        } finally {
            h.dispose();
        }
    }

    @Test
    public void zOrderEventPublishesOnceOnExecuteUndoRedoAndNotForNoopOrRejected() {
        EventFlow.i().flush();
        Harness h = new Harness();
        List<EventFlow.EntityZOrderChanged> events = new ArrayList<>();
        EventFlow.Listener<EventFlow.EntityZOrderChanged> listener = events::add;
        EventFlow.i().subscribe(EventFlow.EntityZOrderChanged.class, listener);
        try {
            int top = h.entity(1);
            int bottom = h.entity(0);
            h.process();

            h.execute(h.order().moveEntity(top, 1));
            EventFlow.i().flush();
            assertEquals(1, events.size());
            assertEquals(0, events.get(0).layerIndex());

            h.history.undo();
            EventFlow.i().flush();
            assertEquals(2, events.size());

            h.history.redo();
            EventFlow.i().flush();
            assertEquals(3, events.size());

            h.history.execute(h.command(h.order().flattenedTopToBottom()));
            EventFlow.i().flush();
            assertEquals(3, events.size());

            h.layerMeta.locked = true;
            h.history.execute(h.command(h.order().moveEntity(bottom, 1)));
            EventFlow.i().flush();
            assertEquals(3, events.size());
            assertEquals(1, h.history.getCursor());
            h.assertZ(bottom, 1);
            h.assertZ(top, 0);
        } finally {
            EventFlow.i().unsubscribe(EventFlow.EntityZOrderChanged.class, listener);
            EventFlow.i().flush();
            h.dispose();
        }
    }

    private static final class Harness {
        final World world = new World(new WorldConfiguration()
                .setSystem(new DirtyTrackerSystem(128)));
        final HistoryManager history = new HistoryManager(32);
        final LayerLogicalOrderService logicalOrder = new LayerLogicalOrderService(world);
        final LayerMetaComponent layerMeta;

        Harness() {
            int layer = world.create();
            world.getMapper(LayerComponent.class).create(layer).layerIndex = 0;
            layerMeta = world.getMapper(LayerMetaComponent.class).create(layer);
            layerMeta.name = "Layer";
            process();
        }

        int entity(int z) {
            int entity = world.create();
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
            index.layerIndex = 0;
            index.zIndex = z;
            world.getMapper(PixscapeIdentityComponent.class).create(entity).name = "E" + entity;
            return entity;
        }

        int member(int z, int instanceId, String prefabId) {
            int entity = entity(z);
            PrefabInstanceComponent prefab =
                    world.getMapper(PrefabInstanceComponent.class).create(entity);
            prefab.instanceId = instanceId;
            prefab.prefabId = prefabId;
            return entity;
        }

        LayerLogicalOrderService.LayerOrder order() { return logicalOrder.derive(0); }

        ReorderLogicalLayerCommand command(IntArray desired) {
            return new ReorderLogicalLayerCommand(world, history.historyIds(), 0, desired);
        }

        void execute(IntArray desired) { history.execute(command(desired)); }
        void normalize() { execute(order().flattenedTopToBottom()); }
        void process() { world.process(); }
        DirtyTrackerSystem dirty() { return world.getSystem(DirtyTrackerSystem.class); }
        void assertZ(int entity, int z) {
            assertEquals(z, world.getMapper(EntityIndexComponent.class).get(entity).zIndex);
        }
        void assertSequential() {
            IntArray values = new IntArray();
            for (int entity : order().flattenedTopToBottom().toArray()) {
                values.add(world.getMapper(EntityIndexComponent.class).get(entity).zIndex);
            }
            values.sort();
            for (int i = 0; i < values.size; i++) assertEquals(i, values.get(i));
        }
        void dispose() { world.dispose(); }
    }
}
