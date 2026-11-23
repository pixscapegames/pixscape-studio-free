package games.pixscape.studio.history.commands;

import games.pixscape.runtime.tiled.TiledEntityRole;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.service.SpatialActorPose;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.spatial.SpatialActorGeometry;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.SpatialActorShapeSupport;

public final class EditSpatialFootprintCommand implements Command, HistoryManager.SupportsNoop {
    public record Snapshot(float altitude, float offsetX, float offsetY, float radius) {
        public static Snapshot capture(SpatialFootprintComponent component) {
            if (component == null) {
                return null;
            }
            return new Snapshot(component.altitude, component.localOffsetXPx, component.localOffsetYPx, component.radiusPx);
        }

        public Snapshot withAltitude(float value) {
            return new Snapshot(value, offsetX, offsetY, radius);
        }

        public Snapshot withOffsetX(float value) {
            return new Snapshot(altitude, value, offsetY, radius);
        }

        public Snapshot withOffsetY(float value) {
            return new Snapshot(altitude, offsetX, value, radius);
        }

        public Snapshot withRadius(float value) {
            return new Snapshot(altitude, offsetX, offsetY, value);
        }

        public boolean sameAs(Snapshot other) {
            if (other == null) {
                return false;
            }
            return equals(other);
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long entityHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean noop;
    private final Array<PivotChange> pivots;

    public EditSpatialFootprintCommand(World world,
                                    HistoryIdRegistry historyIds,
                                    int entityId,
                                    Snapshot before,
                                    Snapshot after) {
        this(world, historyIds, entityId, before, after,
                before != null && after != null && (before.offsetX != after.offsetX || before.offsetY != after.offsetY)
                        ? capturePivots(world, historyIds, entityId) : new Array<>());
        if (!noop && pivots.size > 0) {
            compensate(world, entityId, before, after, pivots);
        }
    }

    private EditSpatialFootprintCommand(World world, HistoryIdRegistry historyIds, int entityId,
                                       Snapshot before, Snapshot after, Array<PivotChange> pivots) {
        this.world = world;
        TiledEntityRole.requireActorCapability(world, entityId, "Footprint");
        this.historyIds = historyIds;
        this.before = before;
        this.after = after;
        this.pivots = pivots;
        this.entityHistoryId = historyIds != null ? historyIds.ensureForEntity(entityId) : -1L;
        this.noop = world == null
                || historyIds == null
                || entityHistoryId <= 0L
                || before == null
                || after == null
                || before.sameAs(after);
    }

    @Override
    public String label() {
        return "Edit Spatial Footprint";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        apply(after, true);
    }

    @Override
    public void undo() {
        apply(before, false);
    }

    private void apply(Snapshot snapshot, boolean forward) {
        if (noop || snapshot == null) {
            return;
        }
        int entityId = resolveEntityId();
        if (entityId < 0) {
            return;
        }

        ComponentMapper<SpatialFootprintComponent> mapper = world.getMapper(SpatialFootprintComponent.class);
        SpatialFootprintComponent component = mapper.getSafe(entityId, null);
        if (component == null) {
            return;
        }

        SpatialFootprintComponent candidate = footprint(snapshot);
        // Prepare the complete multi-visual publication before changing the owner.
        Array<SpatialRepresentationComponent> targets = new Array<>();
        Array<ActorSpatialRectangles> values = new Array<>();
        for (PivotChange pivot : pivots) {
            int e = historyIds.entityOfHistoryId(pivot.id);
            var target = e < 0 || !world.getEntityManager().isActive(e) ? null
                    : world.getMapper(SpatialRepresentationComponent.class).getSafe(e, null);
            if (target == null) {
                return;
            }
            targets.add(target);
            values.add((forward ? pivot.after : pivot.before).copy());
        }
        component.set(candidate);
        for (int i = 0; i < targets.size; i++) {
            targets.get(i).rectangles = values.get(i);
            changed(world, historyIds.entityOfHistoryId(pivots.get(i).id), this);
        }

        changed(world, entityId, this);
    }

    private static void changed(World world, int entityId, Object source) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.order(entityId);
        }
        EventFlow.i().publish(new EventFlow.SpatialActorChanged(entityId, EventFlow.tag(source)));
    }

    private static SpatialFootprintComponent footprint(Snapshot snapshot) {
        var result = new SpatialFootprintComponent();
        result.altitude = snapshot.altitude;
        result.localOffsetXPx = snapshot.offsetX;
        result.localOffsetYPx = snapshot.offsetY;
        result.radiusPx = snapshot.radius;
        result.validate();
        return result;
    }

    private static final class PivotChange {
        final long id;
        final SpatialRepresentationComponent component;
        final ActorSpatialRectangles original, before, after;
        final Array<ActorSpatialRectangleData> sources = new Array<>(), targets = new Array<>();

        PivotChange(HistoryIdRegistry ids, int entity, SpatialRepresentationComponent component) {
            id = ids.ensureForEntity(entity);
            this.component = component;
            original = component.rectangles;
            before = original.copy();
            after = original.copy();
            if (before.staticRectangle != null) {
                sources.add(before.staticRectangle);
                targets.add(after.staticRectangle);
            }
            for (var asset : before.clips) {
                for (var clip : asset.value) {
                    sources.add(clip.value);
                    targets.add(after.get(asset.key, clip.key));
                }
            }
        }
    }

    /** Cold edit boundary: only this owner's subtree, stopping at independent footprint owners. */
    private static Array<PivotChange> capturePivots(World world, HistoryIdRegistry ids, int owner) {
        Array<PivotChange> result = new Array<>();
        if (world == null || ids == null || owner < 0) {
            return result;
        }
        var hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        var topology = hierarchy == null ? null : hierarchy.topology();
        IntArray pending = new IntArray();
        pending.add(owner);
        while (pending.size > 0) {
            int entity = pending.pop();
            if (SpatialActorShapeSupport.owner(world, entity) != owner) {
                continue;
            }
            var representation = world.getMapper(SpatialRepresentationComponent.class).getSafe(entity, null);
            if (representation != null && representation.rectangles != null) {
                result.add(new PivotChange(ids, entity, representation));
            }
            if (topology != null && entity < topology.getEntityCapacity()) {
                for (int child = topology.firstChildEntityId[entity]; child >= 0; child = topology.nextSiblingEntityId[child]) {
                    pending.add(child);
                }
            }
        }
        return result;
    }

    private static void compensate(World world, int owner, Snapshot before, Snapshot after, Array<PivotChange> changes) {
        var hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        var poses = hierarchy == null ? null : hierarchy.worldTransforms();
        var transform = poses != null && poses.isResolved(owner)
                ? world.getMapper(TransformComponent.class).getSafe(owner, null)
                : GameObjectHierarchyCommandSupport.worldTransform(world, IdentityRegistry.boundTo(world), owner);
        var start = new SpatialActorGeometry.Footprint();
        var end = new SpatialActorGeometry.Footprint();
        if (!SpatialActorPose.resolve(owner, transform, footprint(before), poses, start)
                || !SpatialActorPose.resolve(owner, transform, footprint(after), poses, end)) {
            throw new IllegalArgumentException("Footprint edit requires a valid owner pose.");
        }
        // Runtime pivots are projected-pixel offsets, not visual-local vectors or physical XYZ.
        float dx = start.footX - end.footX, dy = start.footY - end.footY;
        for (PivotChange change : changes) {
            for (ActorSpatialRectangleData source : change.sources) {
                if (!Float.isFinite(source.pivotX + dx) || !Float.isFinite(source.pivotY + dy)) {
                    throw new IllegalArgumentException("Footprint edit produces a non-finite shadow pivot.");
                }
            }
        }
        for (PivotChange change : changes) {
            for (int i = 0; i < change.sources.size; i++) {
                var source = change.sources.get(i);
                var target = change.targets.get(i);
                target.pivotX = source.pivotX + dx;
                target.pivotY = source.pivotY + dy;
            }
        }
    }

    /** Captures dependent clips once at pointer-down, then previews only that captured set. */
    public static final class Preview {
        private final World world;
        private final HistoryIdRegistry ids;
        private final long id;
        private final int owner;
        private final SpatialFootprintComponent component;
        private final Snapshot before;
        private Snapshot after;
        private final Array<PivotChange> pivots;
        private boolean published;

        public Preview(World world, HistoryIdRegistry ids, int owner) {
            this.world = world;
            this.ids = ids;
            this.owner = owner;
            id = ids.ensureForEntity(owner);
            component = world.getMapper(SpatialFootprintComponent.class).get(owner);
            before = after = Snapshot.capture(component);
            pivots = capturePivots(world, ids, owner);
        }

        public boolean isCurrent() {
            if (ids.entityOfHistoryId(id) != owner || !world.getEntityManager().isActive(owner)
                    || world.getMapper(SpatialFootprintComponent.class).getSafe(owner, null) != component
                    || !after.equals(Snapshot.capture(component))) {
                return false;
            }
            for (PivotChange pivot : pivots) {
                int e = ids.entityOfHistoryId(pivot.id);
                if (e < 0 || !world.getEntityManager().isActive(e)
                        || world.getMapper(SpatialRepresentationComponent.class).getSafe(e, null) != pivot.component
                        || SpatialActorShapeSupport.owner(world, e) != owner
                        || pivot.component.rectangles != (published ? pivot.after : pivot.original)) {
                    return false;
                }
            }
            return true;
        }

        public void update(Snapshot value) {
            if (!isCurrent()) {
                throw new IllegalStateException("Footprint preview target changed.");
            }
            var candidate = footprint(value);
            compensate(world, owner, before, value, pivots);
            component.set(candidate);
            for (PivotChange pivot : pivots) {
                pivot.component.rectangles = pivot.after;
            }
            after = value;
            published = true;
        }

        public void cancel() {
            if (ids.entityOfHistoryId(id) == owner && world.getEntityManager().isActive(owner)
                    && world.getMapper(SpatialFootprintComponent.class).getSafe(owner, null) == component
                    && after.equals(Snapshot.capture(component))) {
                component.set(footprint(before));
            }
            for (PivotChange pivot : pivots) {
                int e = ids.entityOfHistoryId(pivot.id);
                if (e >= 0 && world.getEntityManager().isActive(e)
                        && world.getMapper(SpatialRepresentationComponent.class).getSafe(e, null) == pivot.component
                        && pivot.component.rectangles == pivot.after) {
                    pivot.component.rectangles = pivot.original;
                }
            }
        }

        public EditSpatialFootprintCommand finish() {
            if (!isCurrent()) {
                throw new IllegalStateException("Footprint preview target changed.");
            }
            cancel();
            return new EditSpatialFootprintCommand(world, ids, owner, before, after, pivots);
        }
    }

    private int resolveEntityId() {
        int entityId = historyIds.entityOfHistoryId(entityHistoryId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return -1;
        }
        return entityId;
    }
}
