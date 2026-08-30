package games.pixscape.studio.history.initializer;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.studio.model.EntityKind;

/** Focused snapshot/materializer for a composition-only Game Object root. */
public final class GameObjectRootInitializer extends AbstractCommonInitializer {
    private String sourceAssetId = "";
    private final PropertySet customProperties = new PropertySet();

    public GameObjectRootInitializer(World world) {
        super(world);
    }

    public GameObjectRootInitializer configure(float x, float y, int layerIndex) {
        hasTransform = true;
        trX = x;
        trY = y;
        trRotationRad = 0f;
        trScaleX = 1f;
        trScaleY = 1f;
        trOriginX = 0f;
        trOriginY = 0f;
        hasEntityIndex = true;
        entityLayerIndex = layerIndex;
        entityZIndex = 0;
        capturedZIndex = false;
        hasIdentity = true;
        identityName = "Game Object";
        hasTags = true;
        tagsSnapshot = new Array<>();
        hasMeta = true;
        metaKind = EntityKind.GAME_OBJECT;
        metaNoteSnapshot = "";
        hasVisibility = false;
        hasAabb = false;
        hasObb = false;
        sourceAssetId = "";
        customProperties.clear();
        return this;
    }

    @Override
    public void syncFrom(int entityId) {
        super.syncFrom(entityId);
        GameObjectComponent root = world.getMapper(GameObjectComponent.class)
                .getSafe(entityId, null);
        if (root == null) {
            throw new IllegalArgumentException("Game Object root snapshot requires GameObjectComponent.");
        }
        sourceAssetId = root.sourceAssetId != null ? root.sourceAssetId : "";
        CustomPropertiesComponent properties = world.getMapper(CustomPropertiesComponent.class)
                .getSafe(entityId, null);
        customProperties.clear();
        if (properties != null && properties.properties != null) {
            customProperties.copyFrom(properties.properties);
        }
    }

    @Override
    public void init(int entityId) {
        super.init(entityId);
        GameObjectComponent root = world.getMapper(GameObjectComponent.class).create(entityId);
        root.sourceAssetId = sourceAssetId;
        CustomPropertiesComponent properties = world.getMapper(CustomPropertiesComponent.class)
                .create(entityId);
        properties.properties.copyFrom(customProperties);
    }

    @Override
    public String label() {
        return "Game Object";
    }
}
