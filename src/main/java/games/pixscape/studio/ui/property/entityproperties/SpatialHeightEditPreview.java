package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.World;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;

/** Transient field preview; restore before publishing the one authored history command. */
final class SpatialHeightEditPreview {
    private final World world;
    private int entity = -1;
    private float originalHeight;

    SpatialHeightEditPreview(World world) { this.world = world; }

    float authoredHeight(int eid) {
        return entity == eid ? originalHeight : world.getMapper(SpatialHeightComponent.class).get(eid).height;
    }

    boolean isActive(int eid) { return entity >= 0 && entity == eid; }

    void updateText(int eid, String text) {
        try { update(eid, Float.valueOf(text.trim())); }
        catch (NumberFormatException invalid) { restore(); }
    }

    void update(int eid, Float value) {
        if (value == null || !Float.isFinite(value)) { restore(); return; }
        SpatialHeightComponent component = world.getMapper(SpatialHeightComponent.class).getSafe(eid, null);
        if (component == null) return;
        if (entity != eid) {
            restore(); entity = eid; originalHeight = component.height;
        }
        component.height = Math.max(0, value);
        markOrder(eid);
    }

    void restore() {
        if (entity < 0) return;
        SpatialHeightComponent component = world.getMapper(SpatialHeightComponent.class).getSafe(entity, null);
        if (component != null) { component.height = originalHeight; markOrder(entity); }
        entity = -1;
    }

    private void markOrder(int eid) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) dirty.order(eid);
    }
}
