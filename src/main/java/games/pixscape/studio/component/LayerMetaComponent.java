package games.pixscape.studio.component;


import com.artemis.PooledComponent;

// IMPORTANT: the runtime shipped to developers will not have this component (UI only)
public final class LayerMetaComponent extends PooledComponent {
    public String name = "unnamed";
    public String description = "";
    public boolean locked = false;


    @Override
    protected void reset() {
        name = "unnamed";
        description = "";
        locked = false;
    }
}
