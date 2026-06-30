package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.TagRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.IconResolver;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import games.pixscape.studio.service.physics.PhysicsPolygonAuthoringService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

import java.util.Objects;

public final class EntityPropertiesContext {
    public final World world;
    public final HistoryManager history;
    public final LayerService layerService;
    public final AtlasStudioService atlasStudioService;
    public final SelectionService selectionService;
    public final PhysicsSelectionService physicsSelectionService;
    public final PhysicsPolygonAuthoringService physicsPolygonAuthoringService;
    public final IconResolver iconResolver;
    public final Runnable markPreviewSaveRequired;
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
    public final ComponentMapper<PhysicsFixturesComponent> mPhysFixtures;
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
                                   LayerService layerService,
                                   AtlasStudioService atlasStudioService,
                                   SelectionService selectionService,
                                   IconResolver iconResolver,
                                   Runnable markPreviewSaveRequired,
                                   int sourceTag) {
        this.world = Objects.requireNonNull(world, "world");
        this.history = Objects.requireNonNull(history, "history");
        this.physicsSelectionService = Objects.requireNonNull(physicsSelectionService, "physicsSelectionService");
        this.physicsPolygonAuthoringService = new PhysicsPolygonAuthoringService(world);
        this.layerService = Objects.requireNonNull(layerService, "layerService");
        this.atlasStudioService = Objects.requireNonNull(atlasStudioService, "atlasStudioService");
        this.selectionService = Objects.requireNonNull(selectionService, "selectionService");
        this.iconResolver = Objects.requireNonNull(iconResolver, "iconResolver");
        this.markPreviewSaveRequired = markPreviewSaveRequired;
        this.sourceTag = sourceTag;

        this.mDimensions = world.getMapper(DimensionsComponent.class);
        this.mMeta = world.getMapper(EntityMetaComponent.class);
        this.mIdentity = world.getMapper(PixscapeIdentityComponent.class);
        this.mTags = world.getMapper(PixscapeTagComponent.class);
        this.identityRegistry = new IdentityRegistry();
        this.tagRegistry = new TagRegistry();
        this.identityRegistry.bind(world);
        this.tagRegistry.bind(world);
        this.identityRegistry.rebuild();
        this.tagRegistry.rebuild();
        this.mTint = world.getMapper(TintComponent.class);
        this.mMat = world.getMapper(RenderMaterialComponent.class);
        this.mAnim = world.getMapper(AnimationComponent.class);
        this.mPhysBody = world.getMapper(PhysicsBodyComponent.class);
        this.mPhysFixtures = world.getMapper(PhysicsFixturesComponent.class);
        this.mPhysRuntime = world.getMapper(PhysicsRuntimeBodyComponent.class);
        this.mSpatialHeight = world.getMapper(SpatialHeightComponent.class);
        this.mTransform = world.getMapper(TransformComponent.class);
        this.mTexRegion = world.getMapper(TextureRegionComponent.class);
        this.mSpriteSource = world.getMapper(AssetRefComponent.class);
        this.mRepeat = world.getMapper(RenderRepeatComponent.class);
        this.dirtyTracker = world.getSystem(DirtyTrackerSystem.class);
    }
}
