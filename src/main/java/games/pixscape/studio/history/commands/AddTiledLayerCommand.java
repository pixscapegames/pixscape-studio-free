package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TiledMapComponent;
import games.pixscape.runtime.component.TiledMapLayerComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.tiled.TiledMapHierarchy;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.initializer.TiledMapInitializer;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.entitygraph.CurrentGameObjectRelations;

import java.util.function.IntConsumer;

/** Creates an empty tile layer using its owning map's grid and the existing Tiled history path. */
public final class AddTiledLayerCommand implements Command {
    private final LayerService layers;
    private final World world;
    private final IdentityRegistry identities;
    private final int mapStableId;
    private final IntConsumer onTargetChanged;
    private long layerHistoryId;
    private DeleteTiledMapCommand undoDeletion;

    public AddTiledLayerCommand(LayerService layers, int mapEntityId, IntConsumer onTargetChanged) {
        this.layers = layers;
        this.world = layers.getWorld();
        this.identities = IdentityRegistry.boundTo(world);
        if (mapEntityId < 0 || !world.getEntityManager().isActive(mapEntityId)
                || !world.getMapper(TiledMapComponent.class).has(mapEntityId)
                || !world.getMapper(PixscapeIdentityComponent.class).has(mapEntityId)) {
            throw new IllegalArgumentException("Select a Tiled map before adding a tile layer.");
        }
        this.mapStableId = world.getMapper(PixscapeIdentityComponent.class).get(mapEntityId).stableId;
        this.onTargetChanged = onTargetChanged;
    }

    @Override
    public String label() {
        return "Add tile layer";
    }

    @Override
    public void redo() {
        int mapEntityId = identities.findByStableId(mapStableId);
        if (mapEntityId < 0 || !world.getMapper(TiledMapComponent.class).has(mapEntityId)) {
            throw new IllegalArgumentException("The owning Tiled map is no longer available.");
        }
        if (undoDeletion != null) {
            undoDeletion.undo();
            return;
        }
        TiledMapComponent map = world.getMapper(TiledMapComponent.class).get(mapEntityId);
        map.validate();
        int sceneLayer = CurrentGameObjectRelations.layerIndex(world, mapEntityId);
        int order = 0;
        var siblings = CurrentGameObjectRelations.collectCurrentChildrenByParentStableId(world, identities)
                .get(mapStableId);
        if (siblings != null) {
            for (int i = 0; i < siblings.size; i++) {
                var index = world.getMapper(EntityIndexComponent.class).get(siblings.get(i));
                if (index != null) {
                    order = Math.max(order, index.zIndex + 1);
                }
            }
        }
        var config = ProjectConfig.getInstance();
        var initializer = new TiledMapInitializer(world, layers.getTiledAllocatorService())
                .configureNew(sceneLayer, map.width, map.height, config.canonicalSceneTagCurrent(),
                        map.projection, map.tileWidth, map.tileHeight, 16);
        int entityId = layers.insertTiledMap(initializer, -1L);
        TiledMapHierarchy.attachLayer(world, identities, mapEntityId, entityId,
                TiledMapLayerComponent.Kind.TILE, order);
        identities.setName(entityId, "Tile Layer");
        layerHistoryId = layers.historyIds().ensureForEntity(entityId);
        if (onTargetChanged != null) {
            onTargetChanged.accept(entityId);
        }
    }

    @Override
    public void undo() {
        if (undoDeletion == null) {
            undoDeletion = new DeleteTiledMapCommand(layers,
                    layers.historyIds().entityOfHistoryId(layerHistoryId), onTargetChanged);
        }
        undoDeletion.redo();
    }
}
