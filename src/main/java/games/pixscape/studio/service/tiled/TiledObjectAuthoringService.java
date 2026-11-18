package games.pixscape.studio.service.tiled;

import com.artemis.Aspect;
import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.CreateEntityCommand;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.history.initializer.GenericEntitySnapshotData;
import games.pixscape.studio.model.EntityKind;

/** Cold creation operations through the existing identity and history authorities. */
public final class TiledObjectAuthoringService {
    private final World world;
    private final HistoryManager history;
    private final IdentityRegistry identities;

    public TiledObjectAuthoringService(World world, HistoryManager history) {
        this.world = world;
        this.history = history;
        identities = IdentityRegistry.boundTo(world);
    }

    public int objectLayer(int selected) {
        if (isObjectLayer(world, selected)) {
            return selected;
        }
        int parent = parent(world, selected);
        return isObjectLayer(world, parent) ? parent : -1;
    }

    public static boolean isObjectLayer(World world, int entity) {
        if (entity < 0 || !world.getEntityManager().isActive(entity)) {
            return false;
        }
        TiledMapLayerComponent layer = world.getMapper(TiledMapLayerComponent.class).get(entity);
        int map = parent(world, entity);
        return layer != null && layer.kind == TiledMapLayerComponent.Kind.OBJECT
                && map >= 0 && world.getMapper(TiledMapComponent.class).has(map);
    }

    public static int parent(World world, int entity) {
        if (entity < 0) {
            return -1;
        }
        GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class).get(entity);
        IdentityRegistry identities = IdentityRegistry.boundTo(world);
        return member == null || identities == null ? -1 : identities.findByStableId(member.parentStableId);
    }

    public int mapFor(int entity) {
        if (entity >= 0 && world.getMapper(TiledMapComponent.class).has(entity)) {
            return entity;
        }
        int parent = parent(world, entity);
        return parent >= 0 && world.getMapper(TiledMapComponent.class).has(parent) ? parent : -1;
    }

    public int createObjectLayer(int map) {
        if (map < 0 || !world.getMapper(TiledMapComponent.class).has(map)) {
            throw new IllegalArgumentException("Select a Tiled map before adding an object layer.");
        }
        GenericEntitySnapshotData data = base(map, EntityKind.GAME_OBJECT, "Object Layer");
        return create(map, data, true);
    }

    public int createPolygon(int layer, float[] localVertices) {
        requireObjectLayer(layer);
        TiledObjectGeometry.validate(localVertices);
        GenericEntitySnapshotData data = base(layer, EntityKind.POLYGON, "Polygon");
        data.polygonVertices = localVertices.clone();
        setDimensions(data, localVertices);
        return create(layer, data, false);
    }

    public float[] rectangleVertices(int layer, float x0, float y0, float x1, float y1) {
        requireObjectLayer(layer);
        TiledMapComponent map = world.getMapper(TiledMapComponent.class).get(parent(world, layer));
        if (map.projection == TiledProjection.ORTHO) {
            return new float[]{Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.min(y0, y1),
                    Math.max(x0, x1), Math.max(y0, y1), Math.min(x0, x1), Math.max(y0, y1)};
        }
        // Invert the two projected map axes; translation cancels between the drag endpoints.
        float ratio = (float) map.tileWidth / map.tileHeight;
        float a = (x1 - x0) / ratio + y1 - y0;
        float b = -(x1 - x0) / ratio + y1 - y0;
        float loA = Math.min(0, a), hiA = Math.max(0, a);
        float loB = Math.min(0, b), hiB = Math.max(0, b);
        return new float[]{x0 + (loA - loB) * ratio * .5f, y0 + (loA + loB) * .5f,
                x0 + (hiA - loB) * ratio * .5f, y0 + (hiA + loB) * .5f,
                x0 + (hiA - hiB) * ratio * .5f, y0 + (hiA + hiB) * .5f,
                x0 + (loA - hiB) * ratio * .5f, y0 + (loA + hiB) * .5f};
    }

    public int createRectangle(int layer, float[] corners) {
        requireObjectLayer(layer);
        TiledObjectGeometry.validate(corners);
        if (corners.length != 8 || Vector2.dst2(corners[0], corners[1], corners[2], corners[3]) < .0001f
                || Vector2.dst2(corners[0], corners[1], corners[6], corners[7]) < .0001f) {
            throw new IllegalArgumentException("Draw a rectangle with two nonzero edges.");
        }
        GenericEntitySnapshotData data = base(layer, EntityKind.TILED_RECTANGLE, "Rectangle");
        setDimensions(data, corners);
        TiledMapComponent map = world.getMapper(TiledMapComponent.class).get(parent(world, layer));
        if (map.projection == TiledProjection.ORTHO) {
            data.x = corners[0];
            data.y = corners[1];
        } else {
            data.polygonVertices = corners.clone();
        }
        return create(layer, data, false);
    }

    private void requireObjectLayer(int layer) {
        if (!isObjectLayer(world, layer)) {
            throw new IllegalArgumentException("Select a Tiled object layer before creating an object.");
        }
    }

    private GenericEntitySnapshotData base(int parent, EntityKind kind, String name) {
        GenericEntitySnapshotData data = new GenericEntitySnapshotData();
        data.hasTransform = true;
        data.scaleX = data.scaleY = 1;
        data.hasEntityIndex = true;
        data.layerIndex = world.getMapper(EntityIndexComponent.class).get(parent).layerIndex;
        data.hasMeta = true;
        data.metaKind = kind.name();
        data.hasIdentity = true;
        data.identityName = name;
        data.hasVisibility = true;
        data.visible = true;
        int parentStable = world.getMapper(PixscapeIdentityComponent.class).get(parent).stableId;
        // Cold insertion: derive sibling order, never identity, from existing authored entries.
        var members = world.getAspectSubscriptionManager().get(Aspect.all(GameObjectMemberComponent.class,
                EntityIndexComponent.class)).getEntities();
        for (int i = 0; i < members.size(); i++) {
            int e = members.get(i);
            if (world.getMapper(GameObjectMemberComponent.class).get(e).parentStableId == parentStable) {
                data.zIndex = Math.max(data.zIndex, world.getMapper(EntityIndexComponent.class).get(e).zIndex + 1);
            }
        }
        return data;
    }

    private void setDimensions(GenericEntitySnapshotData data, float[] vertices) {
        float minX = vertices[0], minY = vertices[1], maxX = minX, maxY = minY;
        for (int i = 2; i < vertices.length; i += 2) {
            minX = Math.min(minX, vertices[i]); maxX = Math.max(maxX, vertices[i]);
            minY = Math.min(minY, vertices[i + 1]); maxY = Math.max(maxY, vertices[i + 1]);
        }
        data.hasDimensions = data.hasAabb = data.hasObb = true;
        data.dimensionsWidth = maxX - minX;
        data.dimensionsHeight = maxY - minY;
    }

    private int create(int parent, GenericEntitySnapshotData data, boolean objectLayer) {
        if (identities == null) {
            throw new IllegalStateException("Object creation requires the Scene identity registry.");
        }
        int parentStable = world.getMapper(PixscapeIdentityComponent.class).get(parent).stableId;
        GenericEntityInitializer initializer = new GenericEntityInitializer(world) {
            @Override public void init(int e) {
                super.init(e);
                world.getMapper(GameObjectMemberComponent.class).create(e).parentStableId = parentStable;
                if (objectLayer) {
                    world.getMapper(GameObjectComponent.class).create(e);
                    world.getMapper(TiledMapLayerComponent.class).create(e).kind = TiledMapLayerComponent.Kind.OBJECT;
                    world.getMapper(LayerParallaxComponent.class).create(e);
                }
                identities.ensureStableId(e);
                identities.indexEntityImmediately(e);
            }
        };
        initializer.applySnapshotData(data);
        CreateEntityCommand command = new CreateEntityCommand(world, history.historyIds(), initializer, null);
        history.execute(command);
        return command.getCreatedEntityId();
    }
}
