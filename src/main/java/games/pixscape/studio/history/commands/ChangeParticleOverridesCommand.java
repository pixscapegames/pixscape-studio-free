package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.ParticleOverridesComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager.SupportsNoop;

import java.util.ArrayList;
import java.util.List;

public final class ChangeParticleOverridesCommand implements Command, SupportsNoop {

    public static final class Snapshot {
        public boolean present;

        public boolean enabled;
        public float sizeMul;
        public float alphaMul;
        public int tintRgba;

        public static Snapshot of(ParticleOverridesComponent c) {
            Snapshot s = new Snapshot();
            if (c == null) {
                s.present = false;
                // values do not matter when present=false
                s.enabled = true;
                s.sizeMul = 1f;
                s.alphaMul = 1f;
                s.tintRgba = -1;
                return s;
            }

            s.present = true;
            s.enabled = c.enabled;
            s.sizeMul = c.sizeMul;
            s.alphaMul = c.alphaMul;
            s.tintRgba = c.tintRgba;
            return s;
        }

        public boolean equalsState(Snapshot o) {
            if (o == null) return false;
            if (present != o.present) return false;
            if (!present) return true; // both absent => equivalent
            return enabled == o.enabled
                    && Float.compare(sizeMul, o.sizeMul) == 0
                    && Float.compare(alphaMul, o.alphaMul) == 0
                    && tintRgba == o.tintRgba;
        }
    }

    private record Entry(long historyId, Snapshot before, Snapshot after) {
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final List<Entry> entries = new ArrayList<>();

    public ChangeParticleOverridesCommand(World world, HistoryIdRegistry historyIds) {
        this.world = world;
        this.historyIds = historyIds;
    }

    @Override
    public String label() {
        return "Particle Overrides";
    }

    public void addEntry(long historyId, Snapshot before, Snapshot after) {
        if (before == null || after == null) return;
        if (before.equalsState(after)) return; // no-op
        entries.add(new Entry(historyId, before, after));
    }

    @Override
    public boolean isNoop() {
        return entries.isEmpty();
    }

    @Override
    public void undo() {
        apply(true);
    }

    @Override
    public void redo() {
        apply(false);
    }

    private void apply(boolean toBefore) {
        ComponentMapper<ParticleOverridesComponent> mOv = world.getMapper(ParticleOverridesComponent.class);

        for (Entry e : entries) {
            int entityId = historyIds.entityOfHistoryId(e.historyId);
            if (entityId == -1 || !world.getEntityManager().isActive(entityId)) continue;

            Snapshot s = toBefore ? e.before : e.after;

            if (!s.present) {
                // initial state = no overrides => remove the component
                if (mOv.has(entityId)) mOv.remove(entityId);
                continue;
            }

            // initial state = overrides present => restore the fields
            ParticleOverridesComponent ov = mOv.getSafe(entityId, null);
            if (ov == null) ov = mOv.create(entityId);

            ov.enabled = s.enabled;
            ov.sizeMul = Math.max(0f, s.sizeMul);
            ov.alphaMul = Math.max(0f, s.alphaMul);
            ov.tintRgba = s.tintRgba;
        }
    }
}
