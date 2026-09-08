package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager.SupportsNoop;

import java.util.ArrayList;
import java.util.List;

public final class ChangeZIndexCommand implements Command, SupportsNoop {

    private record Entry(long historyId, int before, int after) {
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final List<Entry> entries = new ArrayList<>();
    private final DirtyTrackerSystem dirtyTracker;

    public ChangeZIndexCommand(World world, HistoryIdRegistry historyIds) {
        this.world = world;
        this.historyIds = historyIds;
        this.dirtyTracker = world.getSystem(DirtyTrackerSystem.class);
    }

    @Override
    public String label() {
        return "Change ZIndex";
    }

    public void addEntry(long historyId, int before, int after) {
        validateZIndex(before);
        validateZIndex(after);
        if (before == after) {
            return;
        }
        entries.add(new Entry(historyId, before, after));
    }

    private static void validateZIndex(int value) {
        if (value < SortKey64.MIN_Z || value > SortKey64.MAX_Z) {
            throw new IllegalArgumentException("zIndex " + value
                    + " is outside the supported range [" + SortKey64.MIN_Z
                    + ", " + SortKey64.MAX_Z + "].");
        }
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
        ComponentMapper<EntityIndexComponent> mIndex = world.getMapper(EntityIndexComponent.class);
        for (Entry entry : entries) {
            int entityId = historyIds.entityOfHistoryId(entry.historyId);
            if (entityId == -1 || !world.getEntityManager().isActive(entityId)) {
                continue;
            }
            EntityIndexComponent z = mIndex.has(entityId) ? mIndex.get(entityId) : mIndex.create(entityId);
            int value = toBefore ? entry.before : entry.after;
            if (z.zIndex != value) {
                z.zIndex = value;
                if (dirtyTracker != null) {
                    dirtyTracker.order(entityId);
                }
            }
        }
    }
}
