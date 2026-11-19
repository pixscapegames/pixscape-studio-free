package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.GameObjectCompositionSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.SpatialActorShapeSupport;

import java.util.ArrayList;
import java.util.List;

/** One subtree deactivation; stored visual representations and all other components are untouched. */
public final class RemoveSpatialSubtreeCommand implements Command, HistoryManager.SupportsNoop {
    private record Footprint(long id, SpatialFootprintComponent value) {
    }
    private final World world;
    private final HistoryIdRegistry ids;
    private final long root;
    private final List<Footprint> footprints = new ArrayList<>();

    public RemoveSpatialSubtreeCommand(World world, HistoryIdRegistry ids, int entity) {
        if (SpatialActorShapeSupport.ancestorOwner(world, entity) >= 0) {
            throw new IllegalArgumentException("Remove Spatial from the spatial composition root.");
        }
        this.world = world;
        this.ids = ids;
        root = ids.ensureForEntity(entity);
        var entities = SpatialRepresentationInitialization.subtree(world, entity);
        var mapper = world.getMapper(SpatialFootprintComponent.class);
        for (int i = 0; i < entities.size; i++) {
            int owner = entities.get(i);
            if (mapper.has(owner)) {
                footprints.add(new Footprint(ids.ensureForEntity(owner), mapper.get(owner).copy()));
            }
        }
    }

    @Override
    public String label() {
        return "Remove Spatial from composition";
    }
    @Override
    public boolean isNoop() {
        return footprints.isEmpty();
    }
    @Override
    public void redo() {
        apply(false);
    }
    @Override
    public void undo() {
        apply(true);
    }

    private void apply(boolean restore) {
        int rootEntity = ids.entityOfHistoryId(root);
        if (rootEntity < 0 || !world.getEntityManager().isActive(rootEntity)) {
            throw new IllegalStateException("Spatial root is unavailable.");
        }
        int[] entities = new int[footprints.size()];
        for (int i = 0; i < entities.length; i++) {
            entities[i] = ids.entityOfHistoryId(footprints.get(i).id());
            if (entities[i] < 0 || !world.getEntityManager().isActive(entities[i])) {
                throw new IllegalStateException("Spatial footprint owner is unavailable.");
            }
        }
        var hierarchy = world.getSystem(games.pixscape.runtime.system.GameObjectHierarchySystem.class);
        if (hierarchy != null) {
            hierarchy.topologyForRemoval();
        }
        var mapper = world.getMapper(SpatialFootprintComponent.class);
        for (int i = 0; i < entities.length; i++) {
            if (restore) {
                mapper.create(entities[i]).set(footprints.get(i).value());
            } else {
                mapper.remove(entities[i]);
            }
        }
        var composition = world.getSystem(GameObjectCompositionSystem.class);
        if (composition != null) {
            composition.refreshSpatialOwners(rootEntity);
        }
        var dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            for (int entity : entities) {
                dirty.order(entity);
            }
        }
        EventFlow.i().publish(new EventFlow.SpatialActorChanged(rootEntity, EventFlow.tag(this)));
    }
}
