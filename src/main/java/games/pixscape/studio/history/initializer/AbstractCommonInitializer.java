package games.pixscape.studio.history.initializer;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.model.EntityKind;

/**
 * Capture and restore common components used by most entities.
 */
public abstract class AbstractCommonInitializer implements Initializer {

    protected final World world;

    protected boolean hasTransform;
    protected float trX;
    protected float trY;
    protected float trRotationRad;
    protected float trScaleX;
    protected float trScaleY;
    protected float trOriginX;
    protected float trOriginY;

    protected boolean hasEntityIndex;
    protected int entityLayerIndex;
    protected int entityZIndex;
    protected boolean capturedZIndex;

    protected boolean hasIdentity;
    protected String identityName;
    protected int identityStableId = IdentityRegistry.UNASSIGNED_STABLE_ID;

    protected boolean hasTags;
    protected Array<String> tagsSnapshot;

    protected boolean hasMeta;
    protected String metaNoteSnapshot;
    protected EntityKind metaKind = EntityKind.UNKNOWN;

    protected boolean hasVisibility;
    protected boolean visible;

    protected boolean hasAabb;
    protected boolean hasObb;

    protected AbstractCommonInitializer(World world) {
        this.world = world;
    }

    public final boolean hasCapturedZIndex() {
        return capturedZIndex;
    }

    @Override
    public void syncFrom(int entityId) {
        ComponentMapper<TransformComponent> mTransform = world.getMapper(TransformComponent.class);
        ComponentMapper<EntityIndexComponent> mEntityIndex = world.getMapper(EntityIndexComponent.class);
        ComponentMapper<PixscapeIdentityComponent> mIdentity = world.getMapper(PixscapeIdentityComponent.class);
        ComponentMapper<PixscapeTagComponent> mTags = world.getMapper(PixscapeTagComponent.class);
        ComponentMapper<EntityMetaComponent> mMeta = world.getMapper(EntityMetaComponent.class);
        ComponentMapper<VisibilityComponent> mVisibility = world.getMapper(VisibilityComponent.class);
        ComponentMapper<AABBComponent> mAabb = world.getMapper(AABBComponent.class);
        ComponentMapper<OrientedBoundsComponent> mObb = world.getMapper(OrientedBoundsComponent.class);

        if (mTransform.has(entityId)) {
            TransformComponent t = mTransform.get(entityId);
            hasTransform = true;
            trX = t.x;
            trY = t.y;
            trRotationRad = t.rotationRad;
            trScaleX = t.scaleX;
            trScaleY = t.scaleY;
            trOriginX = t.originX;
            trOriginY = t.originY;
        } else {
            hasTransform = false;
        }

        if (mEntityIndex.has(entityId)) {
            EntityIndexComponent index = mEntityIndex.get(entityId);
            hasEntityIndex = true;
            entityLayerIndex = index.getLayerIndex();
            entityZIndex = index.getZIndex();
            capturedZIndex = true;
        } else {
            hasEntityIndex = false;
            capturedZIndex = false;
        }

        if (mIdentity.has(entityId)) {
            PixscapeIdentityComponent identity = mIdentity.get(entityId);
            hasIdentity = true;
            identityName = identity.name != null ? identity.name : "";
            identityStableId = identity.stableId;
        } else {
            hasIdentity = false;
            identityName = null;
            identityStableId = IdentityRegistry.UNASSIGNED_STABLE_ID;
        }

        if (mTags.has(entityId)) {
            PixscapeTagComponent tags = mTags.get(entityId);
            hasTags = true;
            tagsSnapshot = tags.tags != null ? new Array<>(tags.tags) : new Array<>();
        } else {
            hasTags = false;
            tagsSnapshot = null;
        }

        if (mMeta.has(entityId)) {
            EntityMetaComponent meta = mMeta.get(entityId);
            hasMeta = true;
            metaNoteSnapshot = meta.note != null ? meta.note : "";
            metaKind = meta.kind != null ? meta.kind : EntityKind.UNKNOWN;
        } else {
            hasMeta = false;
            metaNoteSnapshot = null;
            metaKind = EntityKind.UNKNOWN;
        }

        if (mVisibility.has(entityId)) {
            VisibilityComponent v = mVisibility.get(entityId);
            hasVisibility = true;
            visible = v.isVisible();
        } else {
            hasVisibility = false;
        }

        hasAabb = mAabb.has(entityId);
        hasObb = mObb.has(entityId);
    }

    @Override
    public void init(int entityId) {
        ComponentMapper<TransformComponent> mTransform = world.getMapper(TransformComponent.class);
        ComponentMapper<EntityIndexComponent> mEntityIndex = world.getMapper(EntityIndexComponent.class);
        ComponentMapper<PixscapeIdentityComponent> mIdentity = world.getMapper(PixscapeIdentityComponent.class);
        ComponentMapper<PixscapeTagComponent> mTags = world.getMapper(PixscapeTagComponent.class);
        ComponentMapper<EntityMetaComponent> mMeta = world.getMapper(EntityMetaComponent.class);
        ComponentMapper<VisibilityComponent> mVisibility = world.getMapper(VisibilityComponent.class);
        ComponentMapper<AABBComponent> mAabb = world.getMapper(AABBComponent.class);
        ComponentMapper<OrientedBoundsComponent> mObb = world.getMapper(OrientedBoundsComponent.class);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);

        if (hasTransform) {
            TransformComponent t = mTransform.has(entityId) ? mTransform.get(entityId) : mTransform.create(entityId);
            t.x = trX;
            t.y = trY;
            t.rotationRad = trRotationRad;
            t.scaleX = trScaleX;
            t.scaleY = trScaleY;
            t.originX = trOriginX;
            t.originY = trOriginY;
            if (dirty != null) dirty.geometry(entityId, GeometryDirty.ALL);
        }

        if (hasEntityIndex) {
            EntityIndexComponent index = mEntityIndex.has(entityId) ? mEntityIndex.get(entityId) : mEntityIndex.create(entityId);
            index.layerIndex = entityLayerIndex;
            index.zIndex = entityZIndex;
            if (dirty != null) {
                dirty.layer(entityId);
                dirty.order(entityId);
            }
        }

        if (hasIdentity) {
            PixscapeIdentityComponent identity = mIdentity.has(entityId) ? mIdentity.get(entityId) : mIdentity.create(entityId);
            identity.name = identityName != null ? identityName : "";
            identity.stableId = identityStableId;
        }

        if (hasTags) {
            PixscapeTagComponent tags = mTags.has(entityId) ? mTags.get(entityId) : mTags.create(entityId);
            if (tags.tags == null) tags.tags = new Array<>();
            else tags.tags.clear();
            if (tagsSnapshot != null) tags.tags.addAll(tagsSnapshot);
        }

        if (hasMeta) {
            EntityMetaComponent meta = mMeta.has(entityId) ? mMeta.get(entityId) : mMeta.create(entityId);
            meta.note = metaNoteSnapshot != null ? metaNoteSnapshot : "";
            meta.kind = metaKind != null ? metaKind : EntityKind.UNKNOWN;
        }

        if (hasVisibility) {
            VisibilityComponent v = mVisibility.has(entityId) ? mVisibility.get(entityId) : mVisibility.create(entityId);
            v.visible = visible;
            v.culledByFrustum = false;
            v.inView = true;
        }

        if (hasAabb && !mAabb.has(entityId)) mAabb.create(entityId);
        if (hasObb && !mObb.has(entityId)) mObb.create(entityId);
    }

    @Override
    public String label() {
        return getClass().getSimpleName();
    }

    public void overrideLayerIndex(int newLayerIndex) {
        hasEntityIndex = true;
        entityLayerIndex = newLayerIndex;
    }

    public void overrideZIndex(int newZIndex) {
        hasEntityIndex = true;
        entityZIndex = newZIndex;
        capturedZIndex = true;
    }

    public void setIdentityStableId(int stableId) {
        hasIdentity = true;
        identityStableId = stableId;
    }
}
