package games.pixscape.studio.event;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.studio.history.commands.TransformOp;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.tiled.TiledToolService;

/**
 * EventFlow = simple typed UI event bus, flushed once per frame.
 * <p>
 * - publish(event) : queues an event (no immediate notification).
 * - flush()        : notifies listeners of all pending events.
 * - subscribe(...) : listens to a given event type (record or class).
 * <p>
 * Events are immutable records declared below.
 */
public final class EventFlow {
    /** Origin tag (useful to avoid listener-side echo loops). */

    // ------------------------------------------------------------------------------------------------
    // Event types (records)
    // ------------------------------------------------------------------------------------------------

    /**
     * Entity selection changed (in the scene, item tree, etc.).
     */
    public record SelectionChanged(
            IntArray ids,      // list of selected entities (copy provided by the emitter)
            int primaryId,     // primary entity (focus), or -1
            SelectionService.SelectionSource source,
            int sourceTag      // optional tag to ignore loops (EventFlow.tag(sender))
    ) {
    }

    /**
     * Active (current) layer changed.
     */
    public record CurrentLayerChanged(
            int layerEntityId, // entityId of the layer entity
            SelectionService.SelectionSource source,
            int sourceTag
    ) {
    }

    /**
     * Active (current) layer changed.
     */
    public record LayerOrderChanged(
            int sourceTag
    ) {
    }

    /**
     * Entity name (sprite, layer, etc.) changed.
     */
    public record EntityNameChanged(
            int entityId,
            String newName,
            int sourceTag
    ) {
    }

    public record EntityChanged(
            int entityId,
            TransformOp op,
            int sourceTag
    ) {
    }

    /**
     * Layer name changed.
     */
    public record LayerNameChanged(
            int layerEntityId,
            String newName,
            int sourceTag
    ) {
    }

    /**
     * Layer lock/unlock changed.
     */
    public record LayerLockChanged(
            int layerEntityId,
            boolean locked,
            int sourceTag
    ) {
    }

    /**
     * Entity visibility changed.
     */
    public record EntityVisibilityChanged(
            int entityId,
            boolean visible,
            int sourceTag
    ) {
    }

    /**
     * Current scene metadata pushed to the UI.
     */
    public record CurrentSceneMeta(
            String sceneName,
            String description,
            int sourceTag
    ) {
    }

    /**
     * Scene name changed from the UI.
     */
    public record SceneNameChanged(
            String oldName,
            String newName,
            int sourceTag
    ) {
    }

    /**
     * Scene description changed from the UI.
     */
    public record SceneDescriptionChanged(
            String sceneName,
            String newDescription,
            int sourceTag
    ) {
    }

    /**
     * Shader list changed (add / delete / rename).
     */
    public record ShaderListChanged(
            int sourceTag
    ) {
    }

    public record CurrentCameraChanged(
            int cameraEntityId,
            int sourceTag
    ) {
    }

    public record ParticleControlRequested(
            int entityId,
            ParticleControlType particleControlType

    ) {
    }

    public record ScenePhysicsEnabledChanged(
            boolean enabled,
            int sourceTag
    ) {
    }

    public record PhysicsBodyStructureChanged(
            int entityId,
            int sourceTag
    ) {
    }

    public record SpatialHeightChanged(
            int entityId,
            int sourceTag
    ) {
    }

    public record RenderRepeatChanged(
            int entityId,
            int sourceTag
    ) {
    }

    public record LayerSpatialDepthChanged(
            int layerEntityId,
            int sourceTag
    ) {
    }

    public record SpatialBlocksChanged(
            int layerEntityId,
            int sourceTag
    ) {
    }

    public record SpatialBlockSelectionChanged(
            int layerEntityId,
            int blockId,
            int sourceTag
    ) {
    }

    public record SceneShowFixturesChanged(
            boolean enabled,
            int sourceTag
    ) {
    }

    public record SceneShowJointsChanged(
            boolean enabled,
            int sourceTag
    ) {
    }

    public record FixtureSelectionChanged(
            int bodyEntityId,
            long fixtureId,
            int sourceTag
    ) {
    }

    public record FixtureParametersChanged(
            int bodyEntityId,
            long fixtureId,
            int sourceTag
    ) {
    }

    public record FixtureSelectionCleared(
            int sourceTag
    ) {
    }

    public record JointParametersChanged(
            int jointEntityId,
            int sourceTag
    ) {
    }

    public record SceneGravityChanged(
            float gx,
            float gy,
            int sourceTag
    ) {
    }

    public record SceneAmbientMulChanged(
            float r,
            float g,
            float b,
            int sourceTag
    ) {
    }

    public record SceneTiledEnabledChanged(
            boolean enabled,
            int sourceTag
    ) {
    }

    public record SceneMapOriginChanged(
            int sourceTag
    ) {
    }

    public record SceneMapResized(
            int sourceTag
    ) {
    }

    public record EditorModeChanged(
            EditorMode mode,
            int sourceTag
    ) {
    }

    public enum EditorMode {
        ENTITY,
        TILE
    }

    public record TiledToolChanged(
            TiledToolService.Mode mode,
            int sourceTag
    ) {
    }

    public record TiledBrushTransformChanged(
            byte sanitized,
            int sourceTag
    ) {
    }

    public record PrefabsChanged(
            int sourceTag
    ) {
    }

    public record LogMessage(
            String text
    ) {
    }

    public enum ParticleControlType {PLAY, PAUSE, RESTART}

    // ------------------------------------------------------------------------------------------------
    // Generic bus
    // ------------------------------------------------------------------------------------------------

    /**
     * Typed generic listener.
     */
    public interface Listener<T> {
        void handle(T event);
    }

    private static final EventFlow INSTANCE = new EventFlow();

    /**
     * Singleton access.
     */
    public static EventFlow i() {
        return INSTANCE;
    }

    /**
     * Origin tag (useful to avoid listener-side echo loops).
     */
    public static int tag(Object sender) {
        return (sender == null) ? 0 : System.identityHashCode(sender);
    }

    private final ObjectMap<Class<?>, Array<Listener<?>>> listeners = new ObjectMap<>();
    private final Array<Object> pendingEvents = new Array<>(false, 32);

    private EventFlow() {
    }

    // ------------------------------------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------------------------------------

    /**
     * Subscribe to an event type (record or class).
     */
    public synchronized <T> void subscribe(Class<T> type, Listener<T> listener) {
        Array<Listener<?>> list = listeners.get(type);
        if (list == null) {
            list = new Array<>(false, 4);
            listeners.put(type, list);
        }
        list.add(listener);
    }

    /**
     * Unsubscribe from an event type.
     */
    public synchronized <T> void unsubscribe(Class<T> type, Listener<T> listener) {
        Array<Listener<?>> list = listeners.get(type);
        if (list == null) return;
        list.removeValue(listener, true);
        if (list.size == 0) {
            listeners.remove(type);
        }
    }

    /**
     * Queues an event. No immediate notification:
     * listeners are called during the next flush().
     */
    public synchronized void publish(Object event) {
        if (event == null) return;
        pendingEvents.add(event);
    }

    /**
     * Call once per frame (after world.process()).
     * Notifies all listeners of pending events.
     */
    public void flush() {
        Array<Object> toDispatch;
        synchronized (this) {
            if (pendingEvents.size == 0) return;
            toDispatch = new Array<>(pendingEvents);
            pendingEvents.clear();
        }

        for (int i = 0; i < toDispatch.size; i++) {
            Object ev = toDispatch.get(i);
            Class<?> type = ev.getClass();

            Array<Listener<?>> list;
            synchronized (this) {
                list = listeners.get(type);
            }
            if (list == null || list.size == 0) continue;

            // Call typed listeners
            for (int j = 0; j < list.size; j++) {
                @SuppressWarnings("unchecked")
                Listener<Object> l = (Listener<Object>) list.get(j);
                l.handle(ev);
            }
        }
    }
}
