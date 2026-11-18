package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.helper.RenderRebindHelper;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.asset.StudioAssetVisual;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Replaces only the image reference and authored size, resolving borrowed pixels before publication. */
public final class ReplaceSpriteTextureCommand implements Command, OutcomeAwareCommand {
    private record State(int assetId, String atlasTag, float width, float height) {
    }

    private final World world;
    private final HistoryIdRegistry ids;
    private final long target;
    private final StudioAssetVisualResolver resolver;
    private final IntConsumer onBound;
    private final State before, after;

    public ReplaceSpriteTextureCommand(World world, HistoryIdRegistry ids, int entity, int assetId,
                                      StudioAssetVisualResolver resolver, IntFunction<AssetMeta> assets,
                                      IntConsumer onBound) {
        if (!isSprite(world, entity, assets)) {
            throw new IllegalArgumentException("Texture replacement requires a sprite");
        }
        this.world = world;
        this.ids = ids;
        this.resolver = resolver;
        this.onBound = onBound;
        AssetRefComponent source = world.getMapper(AssetRefComponent.class).get(entity);
        DimensionsComponent size = world.getMapper(DimensionsComponent.class).getSafe(entity, null);
        if (size == null || !world.getMapper(TextureRegionComponent.class).has(entity)
                || !world.getMapper(RenderMaterialComponent.class).has(entity)) {
            throw new IllegalArgumentException("Sprite rendering data is incomplete");
        }
        before = new State(source.assetId, source.atlasTag, size.width, size.height);
        if (source.assetId == assetId) {
            after = before;
            target = -1;
            return;
        }
        AssetMeta meta = assets.apply(assetId);
        if (meta == null || meta.type() != AssetType.IMAGE) {
            throw new IllegalArgumentException("Select an Image asset");
        }
        StudioAssetVisual visual = requireVisual(assetId, source.atlasTag);
        after = new State(assetId, source.atlasTag, visual.pixelWidth(), visual.pixelHeight());
        target = ids.ensureForEntity(entity);
    }

    /** Content role, independent of hierarchy capability and shared rendering components. */
    public static boolean isSprite(World world, int entity, IntFunction<AssetMeta> assets) {
        if (entity < 0 || !world.getEntityManager().isActive(entity)) {
            return false;
        }
        EntityMetaComponent meta = world.getMapper(EntityMetaComponent.class).getSafe(entity, null);
        if (meta == null || meta.kind != EntityKind.SPRITE
                || world.getMapper(AnimationComponent.class).has(entity)
                || world.getMapper(TiledAnimationComponent.class).has(entity)
                || world.getMapper(ParticleEmitterComponent.class).has(entity)
                || world.getMapper(TiledMapComponent.class).has(entity)
                || world.getMapper(TiledLayerComponent.class).has(entity)
                || world.getMapper(TiledMapLayerComponent.class).has(entity)) {
            return false;
        }
        AssetRefComponent source = world.getMapper(AssetRefComponent.class).getSafe(entity, null);
        AssetMeta image = source == null ? null : assets.apply(source.assetId);
        return source != null && (image == null || image.type() == AssetType.IMAGE);
    }

    private StudioAssetVisual requireVisual(int assetId, String atlasTag) {
        StudioAssetVisual visual = resolver.resolveFirst(assetId, atlasTag);
        if (visual == null || visual.texture() == null || visual.pixelWidth() <= 0 || visual.pixelHeight() <= 0) {
            throw new IllegalArgumentException("Image is unavailable: " + assetId);
        }
        return visual;
    }

    @Override
    public String label() {
        return "Replace sprite texture";
    }

    @Override
    public void redo() {
        redoOutcome();
    }

    @Override
    public void undo() {
        undoOutcome();
    }

    @Override
    public CommandOutcome executeOutcome() {
        return redoOutcome();
    }

    @Override
    public CommandOutcome redoOutcome() {
        return apply(after);
    }

    @Override
    public CommandOutcome undoOutcome() {
        return apply(before);
    }

    private CommandOutcome apply(State state) {
        if (before == after) {
            return CommandOutcome.NO_CHANGE;
        }
        int entity = ids.entityOfHistoryId(target);
        if (entity < 0 || !world.getEntityManager().isActive(entity)) {
            return CommandOutcome.REJECTED;
        }
        AssetRefComponent source = world.getMapper(AssetRefComponent.class).getSafe(entity, null);
        DimensionsComponent size = world.getMapper(DimensionsComponent.class).getSafe(entity, null);
        TextureRegionComponent region = world.getMapper(TextureRegionComponent.class).getSafe(entity, null);
        RenderMaterialComponent material = world.getMapper(RenderMaterialComponent.class).getSafe(entity, null);
        if (source == null || size == null || region == null || material == null) {
            return CommandOutcome.REJECTED;
        }
        StudioAssetVisual visual = requireVisual(state.assetId, state.atlasTag);
        RenderRebindHelper.applyVisual(region, material, visual, state.atlasTag);
        source.assetId = state.assetId;
        source.atlasTag = state.atlasTag;
        size.width = state.width;
        size.height = state.height;
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.geometry(entity, GeometryDirty.ALL);
            dirty.material(entity);
        }
        if (onBound != null) {
            onBound.accept(entity);
        }
        EventFlow.i().publish(new EventFlow.EntityChanged(entity, TransformOp.SCALE, EventFlow.tag(this)));
        return CommandOutcome.APPLIED;
    }
}
