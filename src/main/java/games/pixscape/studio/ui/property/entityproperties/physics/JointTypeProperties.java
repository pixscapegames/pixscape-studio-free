package games.pixscape.studio.ui.property.entityproperties.physics;

import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;

public abstract class JointTypeProperties extends VisTable implements JointSpecificPanel {

    protected final CollapsibleVisTable root = new CollapsibleVisTable(true);
    protected int jointEid = -1;

    protected JointTypeProperties() {
        super(true);
        top().left();
        add(root).growX().row();
    }

    @Override
    public void setJointEid(int jointEid) {
        this.jointEid = jointEid;
    }
}
