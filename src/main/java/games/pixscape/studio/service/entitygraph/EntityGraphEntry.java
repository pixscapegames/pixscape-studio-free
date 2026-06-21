package games.pixscape.studio.service.entitygraph;

import games.pixscape.studio.history.initializer.GenericEntityInitializer;

public record EntityGraphEntry(int sourceEntityId, GenericEntityInitializer initializer) {
}
