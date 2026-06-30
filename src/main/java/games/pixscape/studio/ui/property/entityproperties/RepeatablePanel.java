package games.pixscape.studio.ui.property.entityproperties;

import com.kotcrab.vis.ui.widget.CollapsibleWidget;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.RenderRepeatComponent;
import games.pixscape.studio.history.commands.EditRenderRepeatCommand;
import games.pixscape.studio.ui.widget.UiBinders;

public final class RepeatablePanel extends CollapsibleWidget {
    private final EntityPropertiesContext ctx;
    private final VisTable root = new VisTable(true);

    private final VisCheckBox repeatXBox = new VisCheckBox("Repeat X");
    private final VisCheckBox repeatYBox = new VisCheckBox("Repeat Y");

    private final UiBinders.CheckBoxBinder repeatXBinder;
    private final UiBinders.CheckBoxBinder repeatYBinder;

    private int entityId = -1;

    public RepeatablePanel(EntityPropertiesContext ctx) {
        super();
        this.ctx = ctx;

        setTable(root);
        root.left().top().pad(5);
        root.defaults().left();

        repeatXBinder = new UiBinders.CheckBoxBinder(
                ctx.world,
                repeatXBox,
                this::isRepeatableEntity,
                this::readRepeatX,
                (Integer e, Boolean v) -> applyRepeatChange(e, Boolean.TRUE.equals(v), readRepeatY(e))
        );
        repeatYBinder = new UiBinders.CheckBoxBinder(
                ctx.world,
                repeatYBox,
                this::isRepeatableEntity,
                this::readRepeatY,
                (Integer e, Boolean v) -> applyRepeatChange(e, readRepeatX(e), Boolean.TRUE.equals(v))
        );

        root.add(new VisLabel("Repeat requires rotation 0°.")).left().padBottom(4).row();
        root.add(repeatXBox).left().row();
        root.add(repeatYBox).left().row();
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
        refresh();
    }

    public void refresh() {
        repeatXBinder.setEntityId(entityId);
        repeatYBinder.setEntityId(entityId);
    }

    public boolean isApplicable() {
        return isRepeatableEntity(entityId);
    }

    private boolean isRepeatableEntity(int eid) {
        return eid >= 0
                && ctx.world.getEntityManager().isActive(eid)
                && ctx.mTexRegion.has(eid)
                && ctx.mMat.has(eid);
    }

    private boolean readRepeatX(int eid) {
        RenderRepeatComponent repeat = ctx.mRepeat.getSafe(eid, null);
        return repeat != null && repeat.repeatX;
    }

    private boolean readRepeatY(int eid) {
        RenderRepeatComponent repeat = ctx.mRepeat.getSafe(eid, null);
        return repeat != null && repeat.repeatY;
    }

    private void applyRepeatChange(Integer eid, boolean repeatX, boolean repeatY) {
        if (eid == null || eid < 0 || !isRepeatableEntity(eid)) return;

        EditRenderRepeatCommand.Snapshot before =
                EditRenderRepeatCommand.Snapshot.capture(ctx.mRepeat.getSafe(eid, null));
        EditRenderRepeatCommand.Snapshot after =
                new EditRenderRepeatCommand.Snapshot(repeatX, repeatY);
        EditRenderRepeatCommand command = new EditRenderRepeatCommand(
                ctx.world,
                ctx.history.historyIds(),
                eid,
                before,
                after,
                ctx.markPreviewSaveRequired
        );
        if (!command.isNoop()) {
            ctx.history.execute(command);
        }
        refresh();
    }
}
