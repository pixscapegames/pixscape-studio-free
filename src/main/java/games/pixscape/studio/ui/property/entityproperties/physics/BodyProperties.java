package games.pixscape.studio.ui.property.entityproperties.physics;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.property.entityproperties.EntityPropertiesContext;

public final class BodyProperties extends VisTable {

    private final BodyPanel bodyPanel;

    public BodyProperties(EntityPropertiesContext ctx) {
        super(true);

        this.bodyPanel = new BodyPanel(ctx, false);

        top().left();
        add(new VisLabel("BODY"))
                .center()
                .padBottom(CommonLayout.PROPERTY_SECTION_TITLE_BOTTOM_PAD)
                .row();
        add(bodyPanel).growX().top().left().row();
    }

    public void setEntityId(int entityId) {
        bodyPanel.setEntityId(entityId);
    }
}
