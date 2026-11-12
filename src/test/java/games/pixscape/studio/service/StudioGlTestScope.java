package games.pixscape.studio.service;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.studio.event.EventFlow;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Isolates desktop application globals from headless tests sharing the test worker.
 */
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

    @Override
    public void close() throws Exception {
        // LibGDX keeps the application key even after its last resource is disposed.
        // Release only empty registrations: live resources remain visible to leak regressions.
        if (Gdx.app instanceof Lwjgl3Application) {
            Application application = Gdx.app;
            releaseEmptyRegistration(ShaderProgram.class, "shaders", application);
            releaseEmptyRegistration(Mesh.class, "meshes", application);
            releaseEmptyRegistration(Texture.class, "managedTextures", application);
            releaseEmptyRegistration(TextureArray.class, "managedTextureArrays", application);
            releaseEmptyRegistration(Texture3D.class, "managedTexture3Ds", application);
            releaseEmptyRegistration(Cubemap.class, "managedCubemaps", application);
            releaseEmptyRegistration(GLFrameBuffer.class, "buffers", application);
        }
        listeners.clear();
        listeners.putAll(savedListeners);
        pending.clear();
        pending.addAll(savedPending);
        for (var entry : gdx.entrySet()) entry.getKey().set(null, entry.getValue());
    }

    @SuppressWarnings("unchecked")
    private static void releaseEmptyRegistration(Class<?> type, String name, Application application)
            throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        Object registrations = field.get(null);
        if (registrations instanceof ObjectMap<?, ?>) {
            // Do not use ObjectMap's cached iterator: its Entry retains the last application key.
            var map = (ObjectMap<Application, Array<?>>) registrations;
            Array<?> resources = map.get(application);
            if (resources != null && resources.isEmpty()) map.remove(application);
        } else {
            var map = (Map<Application, Array<?>>) registrations;
            Array<?> resources = map.get(application);
            if (resources != null && resources.isEmpty()) map.remove(application);
        }
    }
}
