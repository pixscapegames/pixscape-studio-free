package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.tiled.TiledObjectGeometry;

public final class EditTiledObjectGeometryCommand implements Command, games.pixscape.studio.history.HistoryManager.SupportsNoop {
    private final World world;
    private final HistoryIdRegistry ids;
    private final long target;
    private final TiledObjectGeometry.Snapshot before, after;

    public EditTiledObjectGeometryCommand(World world, HistoryIdRegistry ids, int entity,
                                          TiledObjectGeometry.Snapshot before, TiledObjectGeometry.Snapshot after) {
        this.world = world;
        this.ids = ids;
        target = ids.ensureForEntity(entity);
        this.before = new TiledObjectGeometry.Snapshot(before.vertices(), before.width(), before.height(), before.pose());
        this.after = new TiledObjectGeometry.Snapshot(after.vertices(), after.width(), after.height(), after.pose());
        if (after.vertices() != null) {
            TiledObjectGeometry.validate(after.vertices());
        }
    }

    @Override public String label() { return "Edit Tiled object geometry"; }
    @Override public boolean isNoop() { return before.sameAs(after); }
    @Override public void redo() { apply(after); }
    @Override public void undo() { apply(before); }

    private void apply(TiledObjectGeometry.Snapshot state) {
        int e = ids.entityOfHistoryId(target);
        if (e < 0 || !world.getEntityManager().isActive(e)) {
            return;
        }
        state.pose().apply(world.getMapper(TransformComponent.class).get(e));
        world.getMapper(TransformComponent.class).get(e).refreshCaches();
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(e);
        dimensions.width = state.width();
        dimensions.height = state.height();
        if (state.vertices() != null) {
            world.getMapper(PolygonComponent.class).create(e).setVertices(state.vertices());
        }
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.geometry(e, GeometryDirty.ALL);
        }
        EventFlow.i().publish(new EventFlow.EntityChanged(e, TransformOp.SCALE, EventFlow.tag(this)));
    }
}
