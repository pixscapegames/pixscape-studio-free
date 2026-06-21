package games.pixscape.studio.history;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry bi-directionnel entityId <-> historyId (hors ECS).
 * <p>
 * - an entityId maps to at most one historyId
 * - a historyId maps to at most one entityId
 * - swap-safe (bind remplace proprement les couples existants)
 */
public final class HistoryIdRegistry {

    // entityId -> historyId
    private final Int2LongOpenHashMap entityToHistory = new Int2LongOpenHashMap();
    // historyId -> entityId
    private final Long2IntOpenHashMap historyToEntity = new Long2IntOpenHashMap();

    private final AtomicLong seq = new AtomicLong(1L);

    public HistoryIdRegistry() {
        entityToHistory.defaultReturnValue(-1L);
        historyToEntity.defaultReturnValue(-1);
    }

    private long nextHistoryId() {
        return seq.getAndIncrement();
    }

    /**
     * Returns the existing historyId, or creates and binds a new one.
     */
    public long ensureForEntity(int entityId) {
        if (entityId < 0) return -1L;
        long hid = entityToHistory.get(entityId);
        if (hid != -1L) return hid;
        hid = nextHistoryId();
        bind(entityId, hid);
        return hid;
    }

    public long historyIdOfEntity(int entityId) {
        return entityToHistory.get(entityId);
    }

    public int entityOfHistoryId(long historyId) {
        return historyToEntity.get(historyId);
    }

    /**
     * Explicitly binds entityId <-> historyId.
     * - if entityId was already bound to another historyId => unbind the old pair
     * - if historyId was already bound to another entity => unbind the old pair
     */
    public void bind(int entityId, long historyId) {
        if (entityId < 0 || historyId <= 0) return;

        // 1) if entityId was already bound, remove the old inverse
        long oldHid = entityToHistory.get(entityId);
        if (oldHid != -1L && oldHid != historyId) {
            historyToEntity.remove(oldHid);
        }

        // 2) if historyId was already bound, remove the old inverse
        int oldEntity = historyToEntity.get(historyId);
        if (oldEntity != -1 && oldEntity != entityId) {
            entityToHistory.remove(oldEntity);
        }

        // 3) poser le nouveau couple
        entityToHistory.put(entityId, historyId);
        historyToEntity.put(historyId, entityId);

        // 4) ensure seq >= historyId+1 (useful when restoring high ids)
        reseed(historyId + 1);
    }

    public void unbindEntity(int entityId) {
        if (entityId < 0) return;
        long hid = entityToHistory.remove(entityId);
        if (hid != -1L) {
            historyToEntity.remove(hid);
        }
    }

    public void unbindHistoryId(long historyId) {
        if (historyId <= 0) return;
        int e = historyToEntity.remove(historyId);
        if (e != -1) {
            entityToHistory.remove(e);
        }
    }

    public void clear() {
        entityToHistory.clear();
        historyToEntity.clear();
        seq.set(1L);
    }

    public void reseed(long nextMin) {
        if (nextMin <= 0) return;
        long current;
        do {
            current = seq.get();
            if (current >= nextMin) return;
        } while (!seq.compareAndSet(current, nextMin));
    }
}
