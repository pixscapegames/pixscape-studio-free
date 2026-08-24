package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.component.PrefabInstanceComponent;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.LayerService;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShiftPrefabInstanceZCommandTest {
    @Test
    public void nonUnitDownIsAtomicAndUndoRedoRestoreExactValuesWithoutTouchingSibling() {
        Harness h = new Harness();
        try {
            int a = h.member(20, 9);
            int b = h.member(17, 9);
            int c = h.member(12, 9);
            int standalone = h.indexed(15, 0);
            h.process();

            ShiftPrefabInstanceZCommand command = h.command(9, -6, a, b, c);
            assertFalse(command.isNoop());
            h.history.execute(command);
            h.assertZ(a, 14); h.assertZ(b, 11); h.assertZ(c, 6);
            h.assertZ(standalone, 15);

            h.history.undo();
            h.assertZ(a, 20); h.assertZ(b, 17); h.assertZ(c, 12);
            h.history.redo();
            h.assertZ(a, 14); h.assertZ(b, 11); h.assertZ(c, 6);
        } finally {
            h.dispose();
        }
    }

    @Test
    public void boundaryCorruptionIncompleteMembershipAndLockedLayerRejectWithoutHistory() {
        Harness h = new Harness();
        try {
            int max = h.member(SortKey64.MAX_Z, 5);
            int other = h.member(4, 5);
            h.process();
            assertTrue(h.command(5, 1, max, other).isNoop());
            assertTrue(h.command(5, -1, max).isNoop());

            h.index(max).zIndex = 3;
            h.prefab(other).instanceId = 6;
            assertTrue(h.command(5, 1, max, other).isNoop());

            h.prefab(other).instanceId = 5;
            h.layerMeta.locked = true;
            ShiftPrefabInstanceZCommand locked = h.command(5, 1, max, other);
            assertTrue(locked.isNoop());
            h.history.execute(locked);
            assertFalse(h.history.canUndo());
            h.assertZ(max, 3);
            h.assertZ(other, 4);
        } finally {
            h.dispose();
        }
    }

    @Test
    public void undoShiftResolvesMembersRecreatedByDeleteUndo() {
        Harness h = new Harness();
        try {
            int a = h.member(12, 14);
            int b = h.member(10, 14);
            int c = h.member(8, 14);
            h.process();
            h.history.execute(h.command(14, 6, a, b, c));
            h.history.execute(new DeleteEntitiesCommand(
                    h.world, h.history.historyIds(), new IntArray(new int[]{a, b, c})));
            h.process();

            h.history.undo();
            h.process();
            h.history.undo();
            h.process();

            IntArray restoredZ = new IntArray();
            var entities = h.world.getAspectSubscriptionManager()
                    .get(com.artemis.Aspect.all(PrefabInstanceComponent.class)).getEntities();
            for (int i = 0; i < entities.size(); i++) {
                int entity = entities.get(i);
                if (h.prefab(entity).instanceId == 14) restoredZ.add(h.index(entity).zIndex);
            }
            restoredZ.sort();
            assertEquals(new IntArray(new int[]{8, 10, 12}), restoredZ);
        } finally {
            h.dispose();
        }
    }

    @Test
    public void prefabCrossesAnotherPrefabWithoutChangingTheSibling() {
        Harness h = new Harness();
        try {
            int a1 = h.member(30, 1);
            int a2 = h.member(27, 1);
            int b1 = h.member(20, 2);
            int b2 = h.member(18, 2);
            h.process();

            h.history.execute(h.command(1, -11, a1, a2));
            h.assertZ(a1, 19); h.assertZ(a2, 16);
            h.assertZ(b1, 20); h.assertZ(b2, 18);

            h.history.execute(h.command(1, 2, a1, a2));
            h.assertZ(a1, 21); h.assertZ(a2, 18);
            h.assertZ(b1, 20); h.assertZ(b2, 18);
        } finally {
            h.dispose();
        }
    }

    @Test
    public void arbitraryDeltaThatWouldOverflowAnyMemberRejectsAtomically() {
        Harness h = new Harness();
        try {
            int maxMember = h.member(100, 7);
            int lowMember = h.member(SortKey64.MIN_Z + 5, 7);
            h.process();
            ShiftPrefabInstanceZCommand command =
                    h.command(7, -101L, maxMember, lowMember);
            assertTrue(command.isNoop());
            h.history.execute(command);
            assertFalse(h.history.canUndo());
            h.assertZ(maxMember, 100);
            h.assertZ(lowMember, SortKey64.MIN_Z + 5);
            assertTrue(h.command(7, Long.MAX_VALUE, maxMember, lowMember).isNoop());
            assertTrue(h.command(7, Long.MIN_VALUE, maxMember, lowMember).isNoop());
        } finally {
            h.dispose();
        }
    }

    private static final class Harness {
        final World world = new World();
        final HistoryManager history = new HistoryManager(16);
        final IdentityRegistry identities = new IdentityRegistry();
        final LayerService layers;
        final LayerMetaComponent layerMeta;

        Harness() {
            identities.bind(world, new SceneMeta());
            identities.rebuild();
            int layer = world.create();
            LayerComponent layerComponent = world.getMapper(LayerComponent.class).create(layer);
            layerComponent.layerIndex = 0;
            layerMeta = world.getMapper(LayerMetaComponent.class).create(layer);
            layerMeta.name = "Layer";
            layers = new LayerService(world, null, history.historyIds(), identities);
            process();
        }

        int member(int z, int instanceId) {
            int entity = indexed(z, 0);
            PrefabInstanceComponent prefab =
                    world.getMapper(PrefabInstanceComponent.class).create(entity);
            prefab.instanceId = instanceId;
            prefab.prefabId = "Castle";
            return entity;
        }

        int indexed(int z, int layer) {
            int entity = world.create();
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
            index.layerIndex = layer;
            index.zIndex = z;
            return entity;
        }

        ShiftPrefabInstanceZCommand command(int instanceId, long delta, int... ids) {
            return new ShiftPrefabInstanceZCommand(
                    world, history.historyIds(), layers, instanceId,
                    new IntArray(ids), delta);
        }

        EntityIndexComponent index(int entity) {
            return world.getMapper(EntityIndexComponent.class).get(entity);
        }

        PrefabInstanceComponent prefab(int entity) {
            return world.getMapper(PrefabInstanceComponent.class).get(entity);
        }

        void assertZ(int entity, int expected) { assertEquals(expected, index(entity).zIndex); }
        void process() { world.process(); }
        void dispose() { world.dispose(); }
    }
}
