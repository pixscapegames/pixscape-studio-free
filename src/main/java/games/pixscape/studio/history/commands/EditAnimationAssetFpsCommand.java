package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.asset.AnimationAssetAuthoringService;
import games.pixscape.studio.service.asset.AnimationAssetEntityReconciler;

import java.util.function.IntConsumer;

public final class EditAnimationAssetFpsCommand
        implements Command, PreExecutionNoopCommand {

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long entityHistoryId;
    private final int animationAssetId;
    private final float beforeFps;
    private final float afterFps;
    private final AnimationAssetAuthoringService animationAssetAuthoringService;
    private final IntConsumer refreshPreview;
    private final Runnable markSaveRequired;
    private final boolean noop;

    public EditAnimationAssetFpsCommand(World world,
                                        HistoryIdRegistry historyIds,
                                        int entityId,
                                        int animationAssetId,
                                        float beforeFps,
                                        float afterFps,
                                        AnimationAssetAuthoringService animationAssetAuthoringService,
                                        IntConsumer refreshPreview,
                                        Runnable markSaveRequired) {
        validateFps(beforeFps);
        validateFps(afterFps);
        if (animationAssetId <= 0) {
            throw new IllegalArgumentException("Animation asset ID must be positive.");
        }
        this.world = world;
        this.historyIds = historyIds;
        this.entityHistoryId = historyIds != null
                ? historyIds.ensureForEntity(entityId)
                : -1L;
        this.animationAssetId = animationAssetId;
        this.beforeFps = beforeFps;
        this.afterFps = afterFps;
        this.animationAssetAuthoringService = animationAssetAuthoringService;
        this.refreshPreview = refreshPreview;
        this.markSaveRequired = markSaveRequired;
        this.noop = world == null
                || historyIds == null
                || entityHistoryId <= 0L
                || animationAssetAuthoringService == null
                || Float.compare(beforeFps, afterFps) == 0;
    }

    @Override
    public String label() {
        return "Edit Animation FPS";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        apply(afterFps);
    }

    @Override
    public void undo() {
        apply(beforeFps);
    }

    private void apply(float fps) {
        if (noop) return;

        AnimationAssetMeta edited = animationAssetAuthoringService.updateFps(
                animationAssetId, fps);
        int reconciled = AnimationAssetEntityReconciler.reconcile(
                world,
                animationAssetId,
                edited,
                refreshPreview,
                entityId -> EventFlow.i().publish(new EventFlow.AnimationChanged(
                        entityId, EventFlow.tag(this))));
        if (reconciled > 0 && markSaveRequired != null) markSaveRequired.run();
    }

    private static void validateFps(float fps) {
        if (!Float.isFinite(fps) || fps <= 0f) {
            throw new IllegalArgumentException("Animation FPS must be finite and greater than zero.");
        }
    }
}
