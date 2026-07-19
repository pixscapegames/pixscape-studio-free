package games.pixscape.studio.history;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HistoryIdRegistryTest {

    @Test
    public void ensureForEntityReturnsStableIdForSameEntity() {
        HistoryIdRegistry registry = new HistoryIdRegistry();

        long first = registry.ensureForEntity(7);
        long second = registry.ensureForEntity(7);

        assertEquals(first, second);
        assertEquals(first, registry.historyIdOfEntity(7));
        assertEquals(7, registry.entityOfHistoryId(first));
        assertEquals(-1L, registry.ensureForEntity(-1));
    }

    @Test
    public void bindReplacesOldEntityMapping() {
        HistoryIdRegistry registry = new HistoryIdRegistry();

        registry.bind(10, 100L);
        registry.bind(10, 200L);

        assertEquals(200L, registry.historyIdOfEntity(10));
        assertEquals(-1, registry.entityOfHistoryId(100L));
        assertEquals(10, registry.entityOfHistoryId(200L));
    }

    @Test
    public void bindReplacesOldHistoryMapping() {
        HistoryIdRegistry registry = new HistoryIdRegistry();

        registry.bind(10, 100L);
        registry.bind(20, 100L);

        assertEquals(-1L, registry.historyIdOfEntity(10));
        assertEquals(100L, registry.historyIdOfEntity(20));
        assertEquals(20, registry.entityOfHistoryId(100L));
    }

    @Test
    public void unbindEntityRemovesBothDirections() {
        HistoryIdRegistry registry = new HistoryIdRegistry();
        registry.bind(10, 100L);

        registry.unbindEntity(10);

        assertEquals(-1L, registry.historyIdOfEntity(10));
        assertEquals(-1, registry.entityOfHistoryId(100L));
    }

    @Test
    public void unbindHistoryIdRemovesBothDirections() {
        HistoryIdRegistry registry = new HistoryIdRegistry();
        registry.bind(10, 100L);

        registry.unbindHistoryId(100L);

        assertEquals(-1L, registry.historyIdOfEntity(10));
        assertEquals(-1, registry.entityOfHistoryId(100L));
    }

    @Test
    public void clearResetsMappingsAndSequence() {
        HistoryIdRegistry registry = new HistoryIdRegistry();
        registry.bind(10, 100L);

        registry.clear();

        assertEquals(-1L, registry.historyIdOfEntity(10));
        assertEquals(-1, registry.entityOfHistoryId(100L));
        assertEquals(1L, registry.ensureForEntity(20));
    }

    @Test
    public void reseedKeepsSequenceMonotonic() {
        HistoryIdRegistry registry = new HistoryIdRegistry();

        registry.reseed(50L);
        assertEquals(50L, registry.ensureForEntity(10));

        registry.reseed(10L);
        assertEquals(51L, registry.ensureForEntity(20));
    }
}
