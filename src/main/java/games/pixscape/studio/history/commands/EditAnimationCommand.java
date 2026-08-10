package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

import java.util.function.IntConsumer;

public final class EditAnimationCommand implements Command, HistoryManager.SupportsNoop {
    public static final class Snapshot {
        public final IntArray animationAssetIds = new IntArray();
        public int activeAssetId;
        public String currentClip = "";
        public float fps = 12f;
        public boolean playing = true;
        public boolean loop = true;
        public float stateTime;
        public int frame = -1;

        public static Snapshot capture(AnimationComponent animation, AssetRefComponent assetRef) {
            Snapshot snapshot = new Snapshot();
            if (animation != null && animation.animationAssetIds != null) {
                snapshot.animationAssetIds.addAll(animation.animationAssetIds);
                snapshot.currentClip = animation.currentClip != null ? animation.currentClip : "";
                snapshot.fps = animation.fps;
                snapshot.playing = animation.playing;
                snapshot.loop = animation.loop;
                snapshot.stateTime = animation.stateTime;
                snapshot.frame = animation.frame;
            }
            snapshot.activeAssetId = assetRef != null ? assetRef.assetId : -1;
            return snapshot;
        }

        public Snapshot copy() {
            Snapshot copy = new Snapshot();
            copy.animationAssetIds.addAll(animationAssetIds);
            copy.activeAssetId = activeAssetId;
            copy.currentClip = currentClip;
            copy.fps = fps;
            copy.playing = playing;
            copy.loop = loop;
            copy.stateTime = stateTime;
            copy.frame = frame;
            return copy;
        }

        public boolean sameAs(Snapshot other) {
            if (other == null
                    || activeAssetId != other.activeAssetId
                    || !currentClip.equals(other.currentClip)
                    || Float.compare(fps, other.fps) != 0
                    || playing != other.playing
                    || loop != other.loop
                    || Float.compare(stateTime, other.stateTime) != 0
                    || frame != other.frame
                    || animationAssetIds.size != other.animationAssetIds.size) {
                return false;
            }
            for (int i = 0; i < animationAssetIds.size; i++) {
                if (animationAssetIds.get(i) != other.animationAssetIds.get(i)) return false;
            }
            return true;
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long entityHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final IntConsumer refreshPreview;
    private final Runnable markSaveRequired;
    private final boolean noop;

    public EditAnimationCommand(World world,
                                HistoryIdRegistry historyIds,
                                int entityId,
                                Snapshot before,
                                Snapshot after,
                                IntConsumer refreshPreview,
                                Runnable markSaveRequired) {
        this.world = world;
        this.historyIds = historyIds;
        this.entityHistoryId = historyIds != null ? historyIds.ensureForEntity(entityId) : -1L;
        this.before = before != null ? before.copy() : new Snapshot();
        this.after = after != null ? after.copy() : new Snapshot();
        this.refreshPreview = refreshPreview;
        this.markSaveRequired = markSaveRequired;
        this.noop = world == null || historyIds == null || entityHistoryId <= 0L
                || this.before.sameAs(this.after);
        validate(this.before);
        validate(this.after);
    }

    @Override
    public String label() {
        return "Edit Animation";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        apply(after);
    }

    @Override
    public void undo() {
        apply(before);
    }

    private void apply(Snapshot snapshot) {
        if (noop) return;
        int entityId = historyIds.entityOfHistoryId(entityHistoryId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) return;

        ComponentMapper<AnimationComponent> mAnimation = world.getMapper(AnimationComponent.class);
        ComponentMapper<AssetRefComponent> mAssetRef = world.getMapper(AssetRefComponent.class);
        AnimationComponent animation = mAnimation.getSafe(entityId, null);
        AssetRefComponent assetRef = mAssetRef.getSafe(entityId, null);
        if (animation == null || assetRef == null) return;

        animation.animationAssetIds.clear();
        animation.animationAssetIds.addAll(snapshot.animationAssetIds);
        assetRef.assetId = snapshot.activeAssetId;
        animation.currentClip = snapshot.currentClip;
        animation.fps = snapshot.fps;
        animation.playing = snapshot.playing;
        animation.loop = snapshot.loop;
        animation.stateTime = snapshot.stateTime;
        animation.frame = snapshot.frame;

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) dirty.mark(entityId, DirtyBits.MATERIAL);
        if (refreshPreview != null) refreshPreview.accept(entityId);
        if (markSaveRequired != null) markSaveRequired.run();
        EventFlow.i().publish(new EventFlow.AnimationChanged(entityId, EventFlow.tag(this)));
    }

    private static void validate(Snapshot snapshot) {
        if (snapshot.animationAssetIds.size == 0) {
            throw new IllegalArgumentException("Animation entity must reference at least one animation asset.");
        }
        if (!snapshot.animationAssetIds.contains(snapshot.activeAssetId)) {
            throw new IllegalArgumentException("Active animation asset must be in animationAssetIds.");
        }
        IntSet uniqueIds = new IntSet();
        for (int i = 0; i < snapshot.animationAssetIds.size; i++) {
            int assetId = snapshot.animationAssetIds.get(i);
            if (assetId <= 0 || !uniqueIds.add(assetId)) {
                throw new IllegalArgumentException(
                        "Animation asset IDs must be positive and unique: " + assetId);
            }
        }
    }
}
