package games.pixscape.studio.service.entitygraph;

import com.artemis.World;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.history.initializer.Initializer;

/** Materializes one prepared graph entry without retaining source hierarchy identities. */
final class EntityGraphHierarchyInitializer implements Initializer {
    private final World world;
    private final GenericEntityInitializer generic;
    private boolean gameObjectRoot;
    private String gameObjectSourceAssetId;
    private int parentStableId;
    private PropertySet customProperties;

    EntityGraphHierarchyInitializer(
            World world,
            GenericEntityInitializer generic,
            boolean gameObjectRoot,
            String gameObjectSourceAssetId,
            int parentStableId,
            PropertySet customProperties) {
        this.world = world;
        this.generic = generic;
        this.gameObjectRoot = gameObjectRoot;
        this.gameObjectSourceAssetId = gameObjectSourceAssetId != null ? gameObjectSourceAssetId : "";
        this.parentStableId = parentStableId;
        this.customProperties = customProperties != null ? customProperties.copy() : null;
    }

    @Override
    public void syncFrom(int entityId) {
        generic.syncFrom(entityId);
        GameObjectComponent gameObject = world.getMapper(GameObjectComponent.class)
                .getSafe(entityId, null);
        gameObjectRoot = gameObject != null;
        gameObjectSourceAssetId = gameObject != null && gameObject.sourceAssetId != null
                ? gameObject.sourceAssetId : "";
        GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                .getSafe(entityId, null);
        parentStableId = member != null ? member.parentStableId : -1;
        CustomPropertiesComponent properties = world.getMapper(CustomPropertiesComponent.class)
                .getSafe(entityId, null);
        customProperties = properties != null && properties.properties != null
                ? properties.properties.copy() : null;
    }

    @Override
    public void init(int entityId) {
        generic.init(entityId);
        if (gameObjectRoot) {
            world.getMapper(GameObjectComponent.class).create(entityId).sourceAssetId = gameObjectSourceAssetId;
        }
        if (parentStableId > 0) {
            world.getMapper(GameObjectMemberComponent.class).create(entityId)
                    .parentStableId = parentStableId;
        }
        if (customProperties != null) {
            world.getMapper(CustomPropertiesComponent.class).create(entityId).properties = customProperties.copy();
        }
    }

    @Override
    public String label() {
        return "Entity Graph Entry";
    }
}
