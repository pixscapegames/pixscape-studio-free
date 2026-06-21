package games.pixscape.studio.ui.property.entityproperties;

import com.kotcrab.vis.ui.widget.CollapsibleWidget;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.studio.history.commands.TransformOp;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.FloatField;
import games.pixscape.studio.ui.widget.TransformFieldFactory;

public class TransformPanel extends CollapsibleWidget {

    private final VisTable root = new VisTable();
    private final EntityPropertiesContext ctx;

    private final FloatField xField;
    private final FloatField yField;
    private final FloatField originxField;
    private final FloatField originyField;
    private final FloatField rotationField;
    private final FloatField scalexField;
    private final FloatField scaleyField;

    private final VisLabel widthLabel = new VisLabel();
    private final VisLabel heightLabel = new VisLabel();

    private int entityId = -1;

    public TransformPanel(EntityPropertiesContext ctx) {
        super();
        this.ctx = ctx;

        setTable(root);
        root.defaults().top().left().pad(5);

        TransformFieldFactory factory = new TransformFieldFactory(ctx.world, ctx.history);

        xField = factory.posX();
        yField = factory.posY();
        originxField = factory.originX();
        originyField = factory.originY();
        rotationField = factory.rotation();
        scalexField = factory.scaleX();
        scalexField.setDisplayDecimals(2);
        scaleyField = factory.scaleY();
        scaleyField.setDisplayDecimals(2);

        root.add(new VisLabel("X:")).width(CommonLayout.LABEL_WIDTH).left();
        root.add(xField).width(CommonLayout.FIELD_WIDTH).left().row();

        root.add(new VisLabel("Y:")).width(CommonLayout.LABEL_WIDTH).left();
        root.add(yField).width(CommonLayout.FIELD_WIDTH).left().row();

        root.add(new VisLabel("Origin X:")).width(CommonLayout.LABEL_WIDTH).left();
        root.add(originxField).width(CommonLayout.FIELD_WIDTH).left().row();

        root.add(new VisLabel("Origin Y:")).width(CommonLayout.LABEL_WIDTH).left();
        root.add(originyField).width(CommonLayout.FIELD_WIDTH).left().row();

        root.add(new VisLabel("Rotation:")).width(CommonLayout.LABEL_WIDTH).left();
        root.add(rotationField).width(CommonLayout.FIELD_WIDTH).left().row();

        root.add(new VisLabel("Scale X:")).width(CommonLayout.LABEL_WIDTH).left();
        root.add(scalexField).width(CommonLayout.FIELD_WIDTH).left().row();

        root.add(new VisLabel("Scale Y:")).width(CommonLayout.LABEL_WIDTH).left();
        root.add(scaleyField).width(CommonLayout.FIELD_WIDTH).left().row();

        root.add(new VisLabel("Width:")).width(CommonLayout.LABEL_WIDTH).left();
        root.add(widthLabel).left().row();

        root.add(new VisLabel("Height:")).width(CommonLayout.LABEL_WIDTH).left();
        root.add(heightLabel).left().row();
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;

        xField.setEntityId(entityId);
        yField.setEntityId(entityId);
        originxField.setEntityId(entityId);
        originyField.setEntityId(entityId);
        rotationField.setEntityId(entityId);
        scalexField.setEntityId(entityId);
        scaleyField.setEntityId(entityId);

        if (entityId < 0) {
            widthLabel.setText("");
            heightLabel.setText("");
            return;
        }

        DimensionsComponent c = ctx.mDimensions.getSafe(entityId, null);
        if (c != null) {
            widthLabel.setText(String.valueOf(c.width));
            heightLabel.setText(String.valueOf(c.height));
        } else {
            widthLabel.setText("");
            heightLabel.setText("");
        }
    }

    public void onFieldsChanged(TransformOp op) {
        switch (op) {
            case MOVE -> {
                xField.refreshFromModel();
                yField.refreshFromModel();
            }
            case SCALE -> {
                scalexField.refreshFromModel();
                scaleyField.refreshFromModel();
            }
            case ROTATE -> rotationField.refreshFromModel();
            case ORIGIN -> {
                originxField.refreshFromModel();
                originyField.refreshFromModel();

                // En mode "sprite position = x - origin", changer l'origin
                // change aussi l'affichage de X/Y.
                xField.refreshFromModel();
                yField.refreshFromModel();
            }
        }
    }
}