package games.pixscape.studio.ui.property.entityproperties;

import com.kotcrab.vis.ui.widget.CollapsibleWidget;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.ParticleEmitterComponent;
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
    private final VisTable originXRow;
    private final VisTable originYRow;
    private final VisTable rotationRow;
    private final VisTable scaleXRow;
    private final VisTable scaleYRow;
    private final VisTable widthRow;
    private final VisTable heightRow;
    private final Cell<VisTable> originXCell;
    private final Cell<VisTable> originYCell;
    private final Cell<VisTable> rotationCell;
    private final Cell<VisTable> scaleXCell;
    private final Cell<VisTable> scaleYCell;
    private final Cell<VisTable> widthCell;
    private final Cell<VisTable> heightCell;

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

        root.add(row("X:", xField)).left().row();
        root.add(row("Y:", yField)).left().row();
        originXRow = row("Origin X:", originxField);
        originYRow = row("Origin Y:", originyField);
        rotationRow = row("Rotation:", rotationField);
        scaleXRow = row("Scale X:", scalexField);
        scaleYRow = row("Scale Y:", scaleyField);
        widthRow = row("Width:", widthLabel);
        heightRow = row("Height:", heightLabel);
        originXCell = root.add(originXRow).left(); root.row();
        originYCell = root.add(originYRow).left(); root.row();
        rotationCell = root.add(rotationRow).left(); root.row();
        scaleXCell = root.add(scaleXRow).left(); root.row();
        scaleYCell = root.add(scaleYRow).left(); root.row();
        widthCell = root.add(widthRow).left(); root.row();
        heightCell = root.add(heightRow).left(); root.row();
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

        boolean particle = entityId >= 0
                && ctx.world.getMapper(ParticleEmitterComponent.class).has(entityId);
        setApplicable(originXCell, originXRow, !particle);
        setApplicable(originYCell, originYRow, !particle);
        setApplicable(rotationCell, rotationRow, !particle);
        setApplicable(scaleXCell, scaleXRow, !particle);
        setApplicable(scaleYCell, scaleYRow, !particle);
        setApplicable(widthCell, widthRow, !particle);
        setApplicable(heightCell, heightRow, !particle);
        root.invalidateHierarchy();

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

    private static VisTable row(String label, Actor value) {
        VisTable row = new VisTable();
        row.add(new VisLabel(label)).width(CommonLayout.LABEL_WIDTH).left();
        row.add(value).width(CommonLayout.FIELD_WIDTH).left();
        return row;
    }

    private static void setApplicable(Cell<VisTable> cell, VisTable row, boolean applicable) {
        cell.setActor(applicable ? row : null);
        cell.pad(applicable ? 5f : 0f);
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
