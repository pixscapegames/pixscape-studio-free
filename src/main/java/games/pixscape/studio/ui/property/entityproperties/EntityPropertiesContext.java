package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.service.TagRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.IconResolver;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import games.pixscape.studio.service.asset.AnimationAssetAuthoringService;
import games.pixscape.studio.service.physics.PhysicsPolygonAuthoringService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public final class EntityPropertiesContext {
    public final World world;
    public final HistoryManager history;
    public final LayerService layerService;
    public final AtlasStudioService atlasStudioService;
    public final SelectionService selectionService;
    public final PhysicsSelectionService physicsSelectionService;
    public final PhysicsService physicsService;
    public final PhysicsPolygonAuthoringService physicsPolygonAuthoringService;
    public final IconResolver iconResolver;
    public final Runnable markCurrentSceneSaveRequired;
    public final IntFunction<AssetMeta> assetMetaLookup;
    public final IntConsumer refreshAnimationPreview;
    public final Supplier<Array<AnimationAssetMeta>> animationAssets;
    public final AnimationAssetAuthoringService animationAssetAuthoringService;
    public final int sourceTag;

    public final ComponentMapper<DimensionsComponent> mDimensions;
    public final ComponentMapper<EntityMetaComponent> mMeta;
    public final ComponentMapper<PixscapeIdentityComponent> mIdentity;
    public final ComponentMapper<PixscapeTagComponent> mTags;

    public final IdentityRegistry identityRegistry;
    public final TagRegistry tagRegistry;

    public final ComponentMapper<TintComponent> mTint;
    public final ComponentMapper<RenderMaterialComponent> mMat;
    public final ComponentMapper<AnimationComponent> mAnim;
    public final ComponentMapper<PhysicsBodyComponent> mPhysBody;
    public final ComponentMapper<PhysicsShapesComponent> mPhysFixtures;
    public final ComponentMapper<PhysicsRuntimeBodyComponent> mPhysRuntime;
    public final ComponentMapper<SpatialHeightComponent> mSpatialHeight;
    public final ComponentMapper<TransformComponent> mTransform;
    public final ComponentMapper<TextureRegionComponent> mTexRegion;
    public final ComponentMapper<AssetRefComponent> mSpriteSource;
    public final ComponentMapper<RenderRepeatComponent> mRepeat;
    public final DirtyTrackerSystem dirtyTracker;

    public EntityPropertiesContext(World world,
                                   HistoryManager history,
                                   PhysicsSelectionService physicsSelectionService,
                                   PhysicsService physicsService,
                                   LayerService layerService,
                                   AtlasStudioService atlasStudioService,
                                   SelectionService selectionService,
                                   IdentityRegistry identityRegistry,
                                   IconResolver iconResolver,
                                   Runnable markCurrentSceneSaveRequired,
                                   IntFunction<AssetMeta> assetMetaLookup,
                                   IntConsumer refreshAnimationPreview,
                                   Supplier<Array<AnimationAssetMeta>> animationAssets,
                                   AnimationAssetAuthoringService animationAssetAuthoringService,
                                   int sourceTag) {
        this.world = Objects.requireNonNull(world, "world");
        this.history = Objects.requireNonNull(history, "history");
        this.physicsSelectionService = Objects.requireNonNull(physicsSelectionService, "physicsSelectionService");
        this.physicsService = Objects.requireNonNull(physicsService, "physicsService");
        this.physicsPolygonAuthoringService = new PhysicsPolygonAuthoringService(world);
        this.layerService = Objects.requireNonNull(layerService, "layerService");
        this.atlasStudioService = Objects.requireNonNull(atlasStudioService, "atlasStudioService");
        this.selectionService = Objects.requireNonNull(selectionService, "selectionService");
        this.iconResolver = Objects.requireNonNull(iconResolver, "iconResolver");
        this.markCurrentSceneSaveRequired = markCurrentSceneSaveRequired;
        this.assetMetaLookup = Objects.requireNonNull(assetMetaLookup, "assetMetaLookup");
        this.refreshAnimationPreview = Objects.requireNonNull(
                refreshAnimationPreview, "refreshAnimationPreview");
        this.animationAssets = Objects.requireNonNull(animationAssets, "animationAssets");
        this.animationAssetAuthoringService = Objects.requireNonNull(
                animationAssetAuthoringService, "animationAssetAuthoringService");
        this.sourceTag = sourceTag;

        this.mDimensions = world.getMapper(DimensionsComponent.class);
        this.mMeta = world.getMapper(EntityMetaComponent.class);
        this.mIdentity = world.getMapper(PixscapeIdentityComponent.class);
        this.mTags = world.getMapper(PixscapeTagComponent.class);
        this.identityRegistry = Objects.requireNonNull(identityRegistry, "identityRegistry");
        this.tagRegistry = new TagRegistry();
        this.tagRegistry.bind(world);
        this.tagRegistry.rebuild();
        this.mTint = world.getMapper(TintComponent.class);
        this.mMat = world.getMapper(RenderMaterialComponent.class);
        this.mAnim = world.getMapper(AnimationComponent.class);
        this.mPhysBody = world.getMapper(PhysicsBodyComponent.class);
        this.mPhysFixtures = world.getMapper(PhysicsShapesComponent.class);
        this.mPhysRuntime = world.getMapper(PhysicsRuntimeBodyComponent.class);
        this.mSpatialHeight = world.getMapper(SpatialHeightComponent.class);
        this.mTransform = world.getMapper(TransformComponent.class);
        this.mTexRegion = world.getMapper(TextureRegionComponent.class);
        this.mSpriteSource = world.getMapper(AssetRefComponent.class);
        this.mRepeat = world.getMapper(RenderRepeatComponent.class);
        this.dirtyTracker = world.getSystem(DirtyTrackerSystem.class);
    }
}
