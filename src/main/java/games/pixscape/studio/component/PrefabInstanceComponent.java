package games.pixscape.studio.component;

import com.artemis.PooledComponent;

/**
 * Studio-only authored metadata grouping scene entities created by one prefab instantiation.
 *
 * <p>This component is not a transform hierarchy, Runtime data, or a live link to the source
 * prefab. It only preserves the visual Studio grouping of entities from the same prefab drop.</p>
 */
public final class PrefabInstanceComponent extends PooledComponent {
    public int instanceId = -1;
    public String prefabId = "";

    @Override
    protected void reset() {
        instanceId = -1;
        prefabId = "";
    }
}
