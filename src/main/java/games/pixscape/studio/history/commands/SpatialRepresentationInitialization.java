package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.entitygraph.CurrentGameObjectRelations;
import games.pixscape.studio.service.spatial.SpatialActorAdmission;

import java.util.ArrayList;
import java.util.List;

/** Command-owned preparation, never invoked by rendering, inspection or deserialization. */
final class SpatialRepresentationInitialization {
    private record Change(long id, boolean present, ActorSpatialRectangles before, ActorSpatialRectangles after) {
    }

    private final World world;
    private final HistoryIdRegistry ids;
    private final List<Change> changes = new ArrayList<>();

    SpatialRepresentationInitialization(World world, HistoryIdRegistry ids) {
        this.world = world;
        this.ids = ids;
    }

    /** Reads current authored ancestry at a command boundary, including unpublished creations. */
    static boolean hasFootprintOwner(World world, int entity) {
        return SpatialActorAdmission.hasFootprintOwner(world, entity);
    }

    /** One cold hierarchy-index construction, then O(affected subtree), with no frame polling. */
    void prepareSubtree(int root) {
        prepareSubtrees(IntArray.with(root));
    }

    void prepareSubtrees(IntArray roots) {
        IntArray entities = subtrees(world, roots);
        for (int i = 0; i < entities.size; i++) {
            prepareEntity(entities.get(i));
        }
    }

    static IntArray subtree(World world, int root) {
        return subtrees(world, IntArray.with(root));
    }

    private static IntArray subtrees(World world, IntArray roots) {
        var identities = IdentityRegistry.boundTo(world);
        var children = identities == null ? null
                : CurrentGameObjectRelations.collectCurrentChildrenByParentStableId(world, identities);
        IntArray pending = new IntArray();
        IntArray result = new IntArray();
        pending.addAll(roots);
        while (pending.size > 0) {
            int entity = pending.pop();
            result.add(entity);
            var identity = world.getMapper(PixscapeIdentityComponent.class).getSafe(entity, null);
            var descendants = children == null || identity == null ? null : children.get(identity.stableId);
            if (descendants != null) {
                pending.addAll(descendants);
            }
        }
        return result;
    }

    void prepareEntity(int entity) {
        var candidate = games.pixscape.runtime.service.SpatialRepresentationAuthoring.prepare(world, entity);
        if (candidate == null) {
            return;
        }
        var current = world.getMapper(SpatialRepresentationComponent.class).getSafe(entity, null);
        var before = current == null || current.rectangles == null ? null : current.rectangles.copy();
        changes.add(new Change(ids.ensureForEntity(entity), current != null, before, candidate));
    }

    void apply(boolean undo) {
        int[] entities = new int[changes.size()];
        ActorSpatialRectangles[] values = new ActorSpatialRectangles[changes.size()];
        for (int i = 0; i < changes.size(); i++) {
            Change change = changes.get(i);
            entities[i] = ids.entityOfHistoryId(change.id());
            if (entities[i] < 0 || !world.getEntityManager().isActive(entities[i])) {
                throw new IllegalStateException("Spatial representation owner is unavailable.");
            }
            var value = undo ? change.before() : change.after();
            values[i] = value == null ? null : value.copy();
        }
        var mapper = world.getMapper(SpatialRepresentationComponent.class);
        for (int i = 0; i < changes.size(); i++) {
            if (undo && !changes.get(i).present()) {
                mapper.remove(entities[i]);
            } else {
                mapper.create(entities[i]).rectangles = values[i];
            }
        }
    }
}
