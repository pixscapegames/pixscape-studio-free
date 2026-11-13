package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.hierarchy.GameObjectPlacement;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.entitygraph.CurrentGameObjectRelations;

/** One explicit rigid placement, with the same validated scope for execution and history. */
public final class PlaceCompositionCommand implements Command, HistoryManager.SupportsNoop {
    private final GameObjectPlacement placement;
    private final boolean noop;

    public PlaceCompositionCommand(World world, IntArray roots, float pivotX, float pivotY,
                                   float dx, float dy, float radians, boolean resetVelocities) {
        this(world, roots, pivotX, pivotY, dx, dy, radians, resetVelocities, null);
    }

    public PlaceCompositionCommand(World world, IntArray roots, float pivotX, float pivotY,
                                   float dx, float dy, float radians, boolean resetVelocities,
                                   HistoryIdRegistry historyIds) {
        if (world == null || roots == null || roots.size == 0) {
            throw new IllegalArgumentException("Composition placement requires a World and selected roots.");
        }
        var layers = world.getAspectSubscriptionManager().get(Aspect.all(LayerComponent.class)).getEntities();
        IntSet lockedLayers = new IntSet();
        for (int i = 0; i < layers.size(); i++) {
            int entity = layers.get(i);
            var meta = world.getMapper(LayerMetaComponent.class).getSafe(entity, null);
            if (meta != null && meta.locked) {
                lockedLayers.add(world.getMapper(LayerComponent.class).get(entity).layerIndex);
            }
        }
        for (int i = 0; i < roots.size; i++) {
            int root = roots.get(i);
            GameObjectHierarchyCommandSupport.requireSupportedMember(world, root);
            if (lockedLayers.contains(CurrentGameObjectRelations.layerIndex(world, root))) {
                throw new IllegalArgumentException("Composition placement is unavailable on a locked layer.");
            }
        }
        GameObjectPlacement.JointIdentities joints = historyIds == null ? null : new GameObjectPlacement.JointIdentities() {
            @Override
            public long capture(int entity) {
                return historyIds.ensureForEntity(entity);
            }

            @Override
            public long keyOf(int entity) {
                return historyIds.historyIdOfEntity(entity);
            }

            @Override
            public int resolve(long key) {
                return historyIds.entityOfHistoryId(key);
            }
        };
        // History owns this snapshot until the entry is discarded. Never borrow the reusable
        // gameplay buffer: another placement must not overwrite this command's undo state.
        placement = GameObjectPlacement.prepare(world, roots, pivotX, pivotY, dx, dy, radians, resetVelocities, joints);
        noop = dx == 0f && dy == 0f && radians == 0f && !resetVelocities;
    }

    @Override
    public String label() {
        return "Place Composition";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        placement.applyAfter();
    }

    @Override
    public void undo() {
        placement.applyBefore();
    }
}
