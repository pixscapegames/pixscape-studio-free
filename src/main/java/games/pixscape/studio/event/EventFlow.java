package games.pixscape.studio.event;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.studio.history.commands.TransformOp;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.StudioEditingMode;
import games.pixscape.studio.service.tiled.TiledToolService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

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

    /** Transient Studio target used by Tiled and map-owned Spatial/Physics tools. */
    public record TiledMapEditingTargetChanged(
            int mapEntityId,
            int sourceTag
    ) {
    }

    /** Published after one authored Tiled Map is inserted or removed. */
    public record TiledMapContentChanged(
            int mapEntityId,
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

    /** Entity z-order values changed within one authored layer. */
    public record EntityZOrderChanged(
            int layerIndex,
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
     * Authored custom properties changed for an entity.
     */
    public record CustomPropertiesChanged(
            int entityId,
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

    /** The authored default HUD association changed for one canonical Scene. */
    public record SceneHudAssociationChanged(
            String sceneTag,
            String hudScreenId,
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

    public record ScenePhysicsPixelsPerMeterChanged(
            float pixelsPerMeter,
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
            int mapEntityId,
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
            int physicsShapeId,
            int sourceTag
    ) {
    }

    public record FixtureParametersChanged(
            int bodyEntityId,
            int physicsShapeId,
            int sourceTag
    ) {
    }

    public record FixtureSelectionCleared(
            int sourceTag
    ) {
    }

    public record PhysicsSelectionReconciled(
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

    public record AnimationChanged(
            int entityId,
            int sourceTag
    ) {
    }

    public record StudioEditingModeChanged(
            StudioEditingMode mode,
            int sourceTag
    ) {
    }

    public record TiledCursorChanged(
            boolean valid,
            int gx,
            int gy,
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

    public record GameObjectsChanged(
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
    private final ThreadLocal<SubscriptionScope> currentSubscriptionScope = new ThreadLocal<>();

    private EventFlow() {
    }

    // ------------------------------------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------------------------------------

    /**
     * Subscribe to an event type (record or class).
     */
    public synchronized <T> void subscribe(Class<T> type, Listener<T> listener) {
        SubscriptionScope scope = currentSubscriptionScope.get();
        Listener<T> registered = listener;
        if (scope != null) {
            registered = event -> {
                if (!scope.closed && scope.active.getAsBoolean()) listener.handle(event);
            };
            Listener<T> scopedListener = registered;
            scope.removals.add(() -> unsubscribe(type, scopedListener));
        }
        Array<Listener<?>> list = listeners.get(type);
        if (list == null) {
            list = new Array<>(false, 4);
            listeners.put(type, list);
        }
        list.add(registered);
    }

    /** Creates a lifecycle-owned group of listeners delivered only while its Scene is active. */
    public SubscriptionScope newSubscriptionScope(BooleanSupplier active) {
        return new SubscriptionScope(active);
    }

    public final class SubscriptionScope implements AutoCloseable {
        private final BooleanSupplier active;
        private final List<Runnable> removals = new ArrayList<>();
        private boolean closed;

        private SubscriptionScope(BooleanSupplier active) {
            this.active = active != null ? active : () -> true;
        }

        public void run(Runnable action) {
            if (closed) throw new IllegalStateException("Event subscription scope is closed.");
            SubscriptionScope previous = currentSubscriptionScope.get();
            currentSubscriptionScope.set(this);
            try {
                action.run();
            } finally {
                if (previous != null) currentSubscriptionScope.set(previous);
                else currentSubscriptionScope.remove();
            }
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            for (Runnable removal : List.copyOf(removals)) removal.run();
            removals.clear();
        }
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

    /** Drops events produced by an unpublished transactional Scene candidate that failed to open. */
    public synchronized void discardPending() {
        pendingEvents.clear();
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
