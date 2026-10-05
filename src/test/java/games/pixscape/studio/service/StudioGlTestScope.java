package games.pixscape.studio.service;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.studio.event.EventFlow;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/** Isolates desktop application globals from headless tests sharing the test worker. */
public final class StudioGlTestScope implements AutoCloseable {
    private final Map<Field, Object> gdx = new LinkedHashMap<>();
    private final ObjectMap<Object, Array<Object>> listeners;
    private final ObjectMap<Object, Array<Object>> savedListeners = new ObjectMap<>();
    private final Array<Object> pending;
    private final Array<Object> savedPending;

    @SuppressWarnings("unchecked")
    public StudioGlTestScope() throws Exception {
        for (Field field : Gdx.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                gdx.put(field, field.get(null));
            }
        }
        Field field = EventFlow.class.getDeclaredField("listeners");
        field.setAccessible(true);
        listeners = (ObjectMap<Object, Array<Object>>) field.get(EventFlow.i());
        for (var entry : listeners) savedListeners.put(entry.key, new Array<>(entry.value));
        field = EventFlow.class.getDeclaredField("pendingEvents");
        field.setAccessible(true);
        pending = (Array<Object>) field.get(EventFlow.i());
        savedPending = new Array<>(pending);
        listeners.clear();
        pending.clear();
    }

    @Override public void close() throws Exception {
        listeners.clear();
        listeners.putAll(savedListeners);
        pending.clear();
        pending.addAll(savedPending);
        for (var entry : gdx.entrySet()) entry.getKey().set(null, entry.getValue());
    }
}
