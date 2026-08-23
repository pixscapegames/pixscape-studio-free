package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.runtime.property.PropertyValue;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager.SupportsNoop;

import java.util.Objects;

/**
 * Applies one complete authored custom-property edit through history.
 */
public final class EditCustomPropertiesCommand implements Command, SupportsNoop {

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long historyId;
    private final boolean beforePresent;
    private final boolean afterPresent;
    private final PropertySet before;
    private final PropertySet after;
    private final int sourceTag;
    private final Runnable markCurrentSceneSaveRequired;

    public EditCustomPropertiesCommand(World world,
                                       HistoryIdRegistry historyIds,
                                       long historyId,
                                       boolean beforePresent,
                                       PropertySet before,
                                       PropertySet after,
                                       int sourceTag,
                                       Runnable markCurrentSceneSaveRequired) {
        this.world = Objects.requireNonNull(world, "world");
        this.historyIds = Objects.requireNonNull(historyIds, "historyIds");
        this.historyId = historyId;
        this.beforePresent = beforePresent;
        this.before = requireCopy(before, "before");
        this.after = requireCopy(after, "after");
        this.afterPresent = !this.after.isEmpty();
        this.sourceTag = sourceTag;
        this.markCurrentSceneSaveRequired = markCurrentSceneSaveRequired;
    }

    @Override
    public String label() {
        return "Edit Custom Properties";
    }

    @Override
    public void redo() {
        apply(afterPresent, after);
    }

    @Override
    public void undo() {
        apply(beforePresent, before);
    }

    @Override
    public boolean isNoop() {
        return beforePresent == afterPresent && sameProperties(before, after);
    }

    private void apply(boolean shouldHaveComponent, PropertySet properties) {
        int entityId = historyIds.entityOfHistoryId(historyId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) return;

        ComponentMapper<CustomPropertiesComponent> mapper =
                world.getMapper(CustomPropertiesComponent.class);
        if (!shouldHaveComponent) {
            if (mapper.has(entityId)) mapper.remove(entityId);
        } else {
            CustomPropertiesComponent component = mapper.has(entityId)
                    ? mapper.get(entityId)
                    : mapper.create(entityId);
            component.properties.copyFrom(properties);
        }
        EventFlow.i().publish(new EventFlow.CustomPropertiesChanged(entityId, sourceTag));
        if (markCurrentSceneSaveRequired != null) {
            markCurrentSceneSaveRequired.run();
        }
    }

    private static PropertySet requireCopy(PropertySet properties, String label) {
        if (properties == null) {
            throw new IllegalArgumentException("Property set '" + label + "' must not be null.");
        }
        properties.validate();
        return properties.copy();
    }

    private static boolean sameProperties(PropertySet first, PropertySet second) {
        if (first.size() != second.size()) return false;
        Array<String> names = new Array<String>();
        first.copyNamesTo(names);
        for (int i = 0; i < names.size; i++) {
            String name = names.get(i);
            PropertyType type = first.typeOf(name);
            if (type != second.typeOf(name)) return false;
            PropertyValue firstValue = first.valueCopy(name);
            PropertyValue secondValue = second.valueCopy(name);
            if (!sameValue(firstValue, secondValue)) return false;
        }
        return true;
    }

    private static boolean sameValue(PropertyValue first, PropertyValue second) {
        if (first == null || second == null || first.type() != second.type()) return false;
        switch (first.type()) {
            case STRING:
                return first.asString().equals(second.asString());
            case BOOLEAN:
                return first.asBoolean() == second.asBoolean();
            case INTEGER:
                return first.asInt() == second.asInt();
            case FLOAT:
                return Float.compare(first.asFloat(), second.asFloat()) == 0;
            case CLASS:
                return first.className().equals(second.className())
                        && sameProperties(first.classPropertiesCopy(), second.classPropertiesCopy());
            default:
                return false;
        }
    }
}
