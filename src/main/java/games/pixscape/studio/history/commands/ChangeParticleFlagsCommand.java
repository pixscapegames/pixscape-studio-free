package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

import java.util.ArrayList;
import java.util.List;

public final class ChangeParticleFlagsCommand implements Command, HistoryManager.SupportsNoop {

    public static final class Snapshot {
        public boolean localSpace;
        public boolean autoStart;
        public boolean looping;
        public boolean paused;
        public boolean playRequested;
        public boolean restartRequested;

        public static Snapshot of(ParticleEmitterComponent c) {
            Snapshot s = new Snapshot();
            s.localSpace = c.localSpace;
            s.autoStart = c.autoStart;
            s.looping = c.looping;
            s.paused = c.paused;
            s.playRequested = c.playRequested;
            s.restartRequested = c.restartRequested;
            return s;
        }

        public boolean equalsState(Snapshot o) {
            if (o == null) return false;
            return localSpace == o.localSpace
                    && autoStart == o.autoStart
                    && looping == o.looping
                    && paused == o.paused
                    && playRequested == o.playRequested
                    && restartRequested == o.restartRequested;
        }
    }

    private record Entry(long historyId, Snapshot before, Snapshot after) {
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final List<Entry> entries = new ArrayList<>();

    public ChangeParticleFlagsCommand(World world, HistoryIdRegistry historyIds) {
        this.world = world;
        this.historyIds = historyIds;
    }

    @Override
    public String label() {
        return "Particle Flags";
    }

    public void addEntry(long historyId, Snapshot before, Snapshot after) {
        if (before == null || after == null) return;
        if (before.equalsState(after)) return;
        entries.add(new Entry(historyId, before, after));
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
        ComponentMapper<ParticleEmitterComponent> mEmitter = world.getMapper(ParticleEmitterComponent.class);

        for (Entry entry : entries) {
            int entityId = historyIds.entityOfHistoryId(entry.historyId);
            if (entityId == -1 || !world.getEntityManager().isActive(entityId)) continue;

            ParticleEmitterComponent emitter = mEmitter.getSafe(entityId, null);
            if (emitter == null) continue;

            Snapshot s = toBefore ? entry.before : entry.after;
            emitter.localSpace = s.localSpace;
            emitter.autoStart = s.autoStart;
            emitter.looping = s.looping;
            emitter.paused = s.paused;
            emitter.playRequested = s.playRequested;
            emitter.restartRequested = s.restartRequested;
        }
    }
}
