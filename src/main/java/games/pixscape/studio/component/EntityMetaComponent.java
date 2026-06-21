package games.pixscape.studio.component;

import com.artemis.PooledComponent;
import games.pixscape.studio.model.EntityKind;

public final class EntityMetaComponent extends PooledComponent {
    public String note = "";
    public EntityKind kind = EntityKind.UNKNOWN;

    @Override
    protected void reset() {
        note = "";
        kind = EntityKind.UNKNOWN;
    }
}
