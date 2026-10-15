package games.pixscape.studio.service;

import com.artemis.World;
import com.badlogic.gdx.math.MathUtils;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.EditLightingCommand;
import games.pixscape.studio.history.commands.EditLightingCommand.Property;

/** One transient light-value preview followed by the existing history command. */
public final class LightValueGesture {
    private final World world;
    private final HistoryManager history;
    private int entity = -1;
    private long historyId;
    private Property property;
    private float before;
    private float pressX, pressY;
    private boolean fine;

    public LightValueGesture(World world, HistoryManager history) {
        this.world = world;
        this.history = history;
    }

    public void begin(int e, Property property, float screenX, float screenY, boolean fine) {
        cancel();
        if (property != Property.HEIGHT && property != Property.INTENSITY && property != Property.INCLINATION)
            throw new IllegalArgumentException("Expected a light gizmo value");
        if (property == Property.INCLINATION && !world.getMapper(ConeLightComponent.class).has(e))
            throw new IllegalArgumentException("Inclination requires a cone light");
        entity = e;
        historyId = history.historyIds().ensureForEntity(e);
        this.property = property;
        before = EditLightingCommand.read(world, e, property);
        pressX = screenX;
        pressY = screenY;
        this.fine = fine;
    }

    public boolean active() { return entity >= 0; }
    public int entity() { return entity; }
    public Property property() { return property; }
    public float pressX() { return pressX; }
    public float pressY() { return pressY; }

    public void preview(float displacement) {
        if (!valid()) { cancel(); return; }
        float value = before + displacement * (fine ? .1f : 1f);
        if (property == Property.INTENSITY) value = Math.max(0f, value);
        if (property == Property.INCLINATION) value = MathUtils.clamp(value,
                ConeLightComponent.MIN_INCLINATION_DEG, ConeLightComponent.MAX_INCLINATION_DEG);
        if (Float.isFinite(value)) EditLightingCommand.previewLightValue(world, entity, property, value);
    }

    public void commit() {
        if (!valid()) { cancel(); return; }
        int e = entity;
        float after = EditLightingCommand.read(world, e, property);
        EditLightingCommand.previewLightValue(world, e, property, before);
        EditLightingCommand command = new EditLightingCommand(world, history.historyIds(), e, property, after);
        entity = -1;
        if (!command.isNoop()) history.execute(command);
    }

    public void cancel() {
        if (valid()) EditLightingCommand.previewLightValue(world, entity, property, before);
        entity = -1;
    }

    private boolean valid() {
        return entity >= 0 && world.getEntityManager().isActive(entity)
                && history.historyIds().entityOfHistoryId(historyId) == entity
                && (property != Property.INCLINATION || world.getMapper(ConeLightComponent.class).has(entity));
    }
}
