package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.ComponentMapper;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.studio.history.commands.EditCustomPropertiesCommand;
import games.pixscape.studio.ui.config.CommonLayout;

/**
 * Reusable common-header row for generic Pixscape custom properties.
 */
final class CustomPropertiesEditorRow extends VisTable {

    private final EntityPropertiesContext ctx;
    private final ComponentMapper<CustomPropertiesComponent> mapper;
    private final VisLabel summaryLabel = new VisLabel();
    private final VisTextButton editButton = new VisTextButton("Edit properties…");
    private int entityId = -1;

    CustomPropertiesEditorRow(EntityPropertiesContext ctx) {
        super(true);
        this.ctx = ctx;
        this.mapper = ctx.mCustomProperties;
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

    void setEntityId(int entityId) {
        this.entityId = entityId;
        refresh();
    }

    void refresh() {
        CustomPropertiesComponent component = entityId >= 0 ? mapper.getSafe(entityId, null) : null;
        int count = component != null && component.properties != null ? component.properties.size() : 0;
        summaryLabel.setText(summaryFor(count));
        editButton.setDisabled(entityId < 0 || !ctx.world.getEntityManager().isActive(entityId));
    }

    static String summaryFor(int count) {
        if (count <= 0) return "0 properties";
        return count == 1 ? "1 property" : count + " properties";
    }

    private void openDialog() {
        if (entityId < 0 || !ctx.world.getEntityManager().isActive(entityId)) return;
        CustomPropertiesComponent component = mapper.getSafe(entityId, null);
        PropertySet source = component != null && component.properties != null
                ? component.properties
                : new PropertySet();
        EditPropertiesDialog dialog = new EditPropertiesDialog(
                "Edit Properties", source, this::apply);
        dialog.show(getStage());
    }

    private void apply(PropertySet after) {
        if (entityId < 0 || !ctx.world.getEntityManager().isActive(entityId)) return;
        CustomPropertiesComponent component = mapper.getSafe(entityId, null);
        boolean beforePresent = component != null;
        PropertySet before = component != null && component.properties != null
                ? component.properties.copy()
                : new PropertySet();
        long historyId = ctx.history.historyIds().ensureForEntity(entityId);
        EditCustomPropertiesCommand command = new EditCustomPropertiesCommand(
                ctx.world,
                ctx.history.historyIds(),
                historyId,
                beforePresent,
                before,
                after,
                ctx.sourceTag,
                ctx.markCurrentSceneSaveRequired
        );
        if (!command.isNoop()) {
            ctx.history.execute(command);
        }
    }
}
