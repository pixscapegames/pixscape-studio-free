package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.scenes.scene2d.Stage;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.asset.StudioAnimationAssets;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import games.pixscape.studio.ui.modal.StudioDialog;
import games.pixscape.studio.ui.property.entityproperties.AnimationClipsDialog;

import java.util.function.Consumer;

/**
 * Shared entry point for the browser and Inspector's copy-based clip editor.
 */
public final class AnimationAssetClipsEditor {
    private AnimationAssetClipsEditor() {
    }

    public static void show(StudioApplicationAdapter app, int assetId, Stage stage) {
        if (app == null || stage == null) return;
        var service = app.getSceneService();
        var database = service.getAssetMetaDatabase();
        if (database == null || !(database.findById(assetId) instanceof AnimationAssetMeta animation)) return;
        String source = animation.sourceRelPath();
        if (source == null || source.isBlank()) return;
        var project = StudioFs.requireStudioProjectDir(ProjectConfig.getInstance());
        int frameCount = AssetPreviewCache.animationFrameFiles(project.child(source)).size;
        create(animation, frameCount, edited -> {
            if (!project.equals(StudioFs.requireStudioProjectDir(ProjectConfig.getInstance()))
                    || service.getAssetMetaDatabase() != database) {
                new StudioDialog("Edit clips")
                        .text("The asset catalog has changed. Reopen the clip editor.")
                        .button("OK").show(stage);
                return;
            }
            service.saveAnimationAssetClips(source, edited, frameCount);
        }).show(stage);
    }

    static AnimationClipsDialog create(AnimationAssetMeta animation, int frameCount,
                                       Consumer<AnimationAssetMeta> save) {
        AnimationAssetMeta edited = StudioAnimationAssets.copyOf(animation);
        return new AnimationClipsDialog(edited, () -> save.accept(edited), Math.max(0, frameCount - 1));
    }
}
