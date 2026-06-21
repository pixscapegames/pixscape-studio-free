package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager.SupportsNoop;
import games.pixscape.studio.model.EntityKind;

import java.util.Objects;

/**
 * History command for editable entity metadata fields (note and kind).
 */
public final class ChangeEntityMetaCommand implements Command, SupportsNoop {

    public static final int MAX_NOTE_LEN = 256;

    public record Snapshot(String note, EntityKind kind) {
        public Snapshot(String note, EntityKind kind) {
            this.note = clampNote(note);
            this.kind = kind != null ? kind : EntityKind.UNKNOWN;
        }

        private static String clampNote(String value) {
            if (value == null) return "";
            if (value.length() <= MAX_NOTE_LEN) return value;
            return value.substring(0, MAX_NOTE_LEN);
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long historyId;
    private final Snapshot before;
    private final Snapshot after;

    public ChangeEntityMetaCommand(World world,
                                   HistoryIdRegistry historyIds,
                                   long historyId,
                                   Snapshot before,
                                   Snapshot after) {
        this.world = Objects.requireNonNull(world, "world");
        this.historyIds = Objects.requireNonNull(historyIds, "historyIds");
        this.historyId = historyId;
        this.before = Objects.requireNonNull(before, "before");
        this.after = Objects.requireNonNull(after, "after");
    }

    @Override
    public String label() {
        return "Edit Entity Note/Kind";
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
        return before.equals(after);
    }

    private void apply(Snapshot snapshot) {
        int entityId = historyIds.entityOfHistoryId(historyId);
        if (entityId == -1 || !world.getEntityManager().isActive(entityId)) return;

        ComponentMapper<EntityMetaComponent> mapper = world.getMapper(EntityMetaComponent.class);
        EntityMetaComponent meta = mapper.has(entityId) ? mapper.get(entityId) : mapper.create(entityId);

        meta.note = snapshot.note();
        meta.kind = snapshot.kind();
    }

    public static Snapshot snapshotOf(EntityMetaComponent meta) {
        if (meta == null) return new Snapshot("", EntityKind.UNKNOWN);
        return new Snapshot(meta.note, meta.kind);
    }
}
