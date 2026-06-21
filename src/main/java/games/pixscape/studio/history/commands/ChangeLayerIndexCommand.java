package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager.SupportsNoop;

import java.util.ArrayList;
import java.util.List;

public final class ChangeLayerIndexCommand implements Command, SupportsNoop {

    private record Entry(long historyId, int beforeLayerIndex, int afterLayerIndex) {
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final List<Entry> entries = new ArrayList<>();
    private final DirtyTrackerSystem dirtyTracker;

    public ChangeLayerIndexCommand(World world, HistoryIdRegistry historyIds) {
        this.world = world;
        this.historyIds = historyIds;
        this.dirtyTracker = world.getSystem(DirtyTrackerSystem.class);
    }

    @Override
    public String label() {
        return "Change Layer";
    }

    public void addEntry(long historyId, int beforeLayerIndex, int afterLayerIndex) {
        if (beforeLayerIndex == afterLayerIndex) {
            return;
        }
        entries.add(new Entry(historyId, beforeLayerIndex, afterLayerIndex));
    }

    @Override
    public boolean isNoop() {
        return entries.isEmpty();
    }

    @Override
    public void redo() {
        apply(false);
    }

    @Override
    public void undo() {
        apply(true);
    }

    private void apply(boolean toBefore) {
        ComponentMapper<EntityIndexComponent> mLayerIndex = world.getMapper(EntityIndexComponent.class);
        for (Entry entry : entries) {
            int entityId = historyIds.entityOfHistoryId(entry.historyId);
            if (entityId == -1 || !world.getEntityManager().isActive(entityId)) {
                continue;
            }
            EntityIndexComponent layerIndex = mLayerIndex.has(entityId)
                    ? mLayerIndex.get(entityId)
                    : mLayerIndex.create(entityId);
            int value = toBefore ? entry.beforeLayerIndex : entry.afterLayerIndex;
            if (layerIndex.layerIndex != value) {
                layerIndex.layerIndex = value;
                if (dirtyTracker != null) {
                    dirtyTracker.layer(entityId);
                    dirtyTracker.order(entityId);
                }
            }
        }
    }
}
