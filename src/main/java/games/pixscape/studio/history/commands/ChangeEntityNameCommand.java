package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager.SupportsNoop;

import java.util.Objects;

/**
 * History command for entity identity name changes.
 */
public final class ChangeEntityNameCommand implements Command, SupportsNoop {

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long historyId;
    private final String before;
    private final String after;
    private final int sourceTag;

    public ChangeEntityNameCommand(World world,
                                   HistoryIdRegistry historyIds,
                                   long historyId,
                                   String before,
                                   String after,
                                   int sourceTag) {
        this.world = Objects.requireNonNull(world, "world");
        this.historyIds = Objects.requireNonNull(historyIds, "historyIds");
        this.historyId = historyId;
        this.before = normalize(before);
        this.after = normalize(after);
        this.sourceTag = sourceTag;
    }

    @Override
    public String label() {
        return "Edit Entity Name";
    }

    @Override
    public void redo() {
        apply(after);
    }

    @Override
    public void undo() {
        apply(before);
    }

    @Override
    public boolean isNoop() {
        return Objects.equals(before, after);
    }

    private void apply(String value) {
        int entityId = historyIds.entityOfHistoryId(historyId);
        if (entityId == -1 || !world.getEntityManager().isActive(entityId)) return;

        ComponentMapper<PixscapeIdentityComponent> mapper = world.getMapper(PixscapeIdentityComponent.class);
        PixscapeIdentityComponent identity = mapper.has(entityId) ? mapper.get(entityId) : mapper.create(entityId);
        identity.name = value;

        EventFlow.i().publish(new EventFlow.EntityNameChanged(entityId, value, sourceTag));
    }

    private static String normalize(String value) {
        return value != null ? value : "";
    }
}
