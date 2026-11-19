package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.EditCustomPropertiesCommand;
import games.pixscape.studio.ui.config.CommonLayout;

/**
 * Reusable common-header row for generic Pixscape custom properties.
 */
public final class CustomPropertiesEditorRow extends VisTable {

    private final World world;
    private final HistoryManager history;
    private final int sourceTag;
    private final Runnable markSaveRequired;
    private final ComponentMapper<CustomPropertiesComponent> mapper;
    private final VisLabel summaryLabel = new VisLabel();
    private final VisTextButton editButton = new VisTextButton("Edit properties…");
    private int entityId = -1;

    public CustomPropertiesEditorRow(World world, HistoryManager history, int sourceTag, Runnable markSaveRequired) {
        super(true);
        this.world = world;
        this.history = history;
        this.sourceTag = sourceTag;
        this.markSaveRequired = markSaveRequired;
        this.mapper = world.getMapper(CustomPropertiesComponent.class);
        editButton.setName("editCustomProperties");
        summaryLabel.setName("customPropertiesSummary");
        EventFlow.i().subscribe(EventFlow.CustomPropertiesChanged.class, event -> {
            if (event.entityId() == entityId) {
                refresh();
            }
        });
        left();
        add(summaryLabel).left().width(100).growX();
        editButton.setColor(CommonLayout.BUTTON_COLOR);
        editButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openDialog();
            }
        });
        add(editButton).right();
        refresh();
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
        refresh();
    }

    private void refresh() {
        CustomPropertiesComponent component = entityId >= 0 ? mapper.getSafe(entityId, null) : null;
        int count = component != null && component.properties != null ? component.properties.size() : 0;
        summaryLabel.setText(summaryFor(count));
        editButton.setDisabled(entityId < 0 || !world.getEntityManager().isActive(entityId));
    }

    static String summaryFor(int count) {
        if (count <= 0) {
            return "0 properties";
        }
        return count == 1 ? "1 property" : count + " properties";
    }

    private void openDialog() {
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return;
        }
        CustomPropertiesComponent component = mapper.getSafe(entityId, null);
        PropertySet source = component != null && component.properties != null
                ? component.properties
                : new PropertySet();
        EditPropertiesDialog dialog = new EditPropertiesDialog(
                "Edit Properties", source, world, this::apply);
        dialog.show(getStage());
    }

    private void apply(PropertySet after) {
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return;
        }
        CustomPropertiesComponent component = mapper.getSafe(entityId, null);
        boolean beforePresent = component != null;
        PropertySet before = component != null && component.properties != null
                ? component.properties.copy()
                : new PropertySet();
        long historyId = history.historyIds().ensureForEntity(entityId);
        EditCustomPropertiesCommand command = new EditCustomPropertiesCommand(
                world,
                history.historyIds(),
                historyId,
                beforePresent,
                before,
                after,
                sourceTag,
                markSaveRequired
        );
        if (!command.isNoop()) {
            history.execute(command);
        }
    }
}
