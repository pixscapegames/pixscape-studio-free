package games.pixscape.studio.service.entitygraph;

import games.pixscape.runtime.property.PropertySet;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;

/** One clipboard-local entity and its optional clipboard-local parent. */
public record EntityGraphEntry(
        int sourceEntityId,
        int parentSourceEntityId,
        boolean gameObjectRoot,
        String gameObjectSourceAssetId,
        GenericEntityInitializer initializer,
        PropertySet customProperties) {

    /** Materialization always binds a fresh initializer to the destination World. */
    public GenericEntityInitializer initializerFor(com.artemis.World destination) {
        return initializer.duplicateFor(java.util.Objects.requireNonNull(destination, "destination"));
    }

    public EntityGraphEntry(int sourceEntityId, GenericEntityInitializer initializer) {
        this(sourceEntityId, -1, false, "", initializer, null);
    }

    public EntityGraphEntry(
            int sourceEntityId,
            int parentSourceEntityId,
            boolean gameObjectRoot,
            GenericEntityInitializer initializer,
            PropertySet customProperties) {
        this(sourceEntityId, parentSourceEntityId, gameObjectRoot, "", initializer,
                customProperties);
    }

    public EntityGraphEntry {
        if (sourceEntityId < 0) {
            throw new IllegalArgumentException("Entity graph source ID must not be negative.");
        }
        if (parentSourceEntityId == sourceEntityId) {
            throw new IllegalArgumentException("Entity graph entry cannot parent itself.");
        }
        if (initializer == null) {
            throw new IllegalArgumentException("Entity graph initializer is required.");
        }
        // Clipboard payloads keep authored snapshots, never a source World or its mappers.
        initializer = initializer.duplicateFor(null);
        gameObjectSourceAssetId = gameObjectSourceAssetId != null ? gameObjectSourceAssetId : "";
        customProperties = customProperties != null ? customProperties.copy() : null;
    }
}
