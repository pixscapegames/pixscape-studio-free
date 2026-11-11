package games.pixscape.studio.ui.property;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AnimationClipMeta;
import games.pixscape.studio.asset.AssetDisplayInfo;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.service.asset.StudioAnimationAssets;
import games.pixscape.studio.ui.asset.AnimatedThumbnail;
import games.pixscape.studio.ui.asset.AssetPreviewCache;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.SimpleSelectBox;

import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Read-only asset inspection. Clip selection and playback belong exclusively to this view. */
public final class AnimationAssetProperties extends VisTable {
    private final Supplier<AssetMetaDatabase> databaseSource;
    private final Function<String, Array<Texture>> frameLoader;
    private final VisLabel name = new VisLabel();
    private final VisLabel id = new VisLabel();
    private final VisLabel source = new VisLabel();
    private final VisLabel frameCount = new VisLabel();
    private final VisTable previewHolder = new VisTable();
    private final SimpleSelectBox<String> clipBox = new SimpleSelectBox<>();
    private boolean refreshingClips;
    // This is a UI action, not an ECS field; there is no bound button wrapper.
    private final VisTextButton configure = new VisTextButton("Edit");
    private AssetMetaDatabase database;
    private AnimationAssetMeta animation;
    private int assetId = -1;
    private String selectedClip;
    private float displayedFps;
    private Array<Texture> frames = new Array<>();
    private AnimatedThumbnail preview;

    public AnimationAssetProperties(Supplier<AssetMetaDatabase> databaseSource, IntConsumer editClips) {
        this(databaseSource, editClips, AssetPreviewCache::animationFrames);
    }

    AnimationAssetProperties(Supplier<AssetMetaDatabase> databaseSource, IntConsumer editClips,
                             Function<String, Array<Texture>> frameLoader) {
        super(true);
        this.databaseSource = databaseSource;
        this.frameLoader = frameLoader;
        top().left();
        add(new VisLabel("ANIMATION")).center().row();
        CollapsibleVisTable section = new CollapsibleVisTable(true);
        VisTable details = section.content();
        details.top().left();
        name.setName("animationAssetName");
        id.setName("animationAssetId");
        source.setName("animationAssetSource");
        frameCount.setName("animationAssetFrames");
        name.setWrap(true);
        source.setWrap(true);
        details.add(new VisLabel("Name:")).left();
        details.add(name).growX().row();
        details.add(new VisLabel("ID:")).left();
        details.add(id).left().row();
        details.add(new VisLabel("Source:")).left();
        details.add(source).growX().row();
        details.add(new VisLabel("Frames:")).left();
        details.add(frameCount).left().row();
        details.add(previewHolder).colspan(2).growX().height(160).row();
        VisTable clips = new VisTable(true);
        clips.left();
        clipBox.setName("animationPreviewClip");
        clipBox.bind(() -> selectedClip, this::selectClip);
        clips.add(new VisLabel("Clip:")).left();
        clips.add(clipBox).width(120).left();
        configure.setName("configureAnimationClips");
        clips.add(configure).left().row();
        details.add(clips).colspan(2).growX().row();
        add(section).growX().row();
        configure.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                refreshIfChanged();
                if (animation != null) editClips.accept(assetId);
            }
        });
    }

    public void select(int assetId) {
        if (this.assetId != assetId) selectedClip = null;
        this.assetId = assetId;
        refresh();
    }

    void clearProject() {
        stopPreview();
        database = null;
        animation = null;
        assetId = -1;
        selectedClip = null;
    }

    /** O(1) check at the Inspector's post-ECS refresh boundary; no per-frame source scans. */
    public void refreshIfChanged() {
        AssetMetaDatabase next = databaseSource.get();
        var meta = next != null ? next.findById(assetId) : null;
        if (database != next || animation != meta || (animation != null && displayedFps != animation.fps)) {
            refresh();
        }
    }

    private void refresh() {
        stopPreview();
        database = databaseSource.get();
        animation = database != null && database.findById(assetId) instanceof AnimationAssetMeta meta ? meta : null;

        configure.setDisabled(animation == null);
        if (animation == null) {
            name.setText("Unavailable");
            id.setText("");
            source.setText("");
            frameCount.setText("");
            selectedClip = null;
            refreshClipBox(new Array<>());
            return;
        }
        displayedFps = animation.fps;
        name.setText(AssetDisplayInfo.from(animation).displayName());
        id.setText(Integer.toString(animation.id()));
        source.setText(animation.sourceRelPath() != null ? animation.sourceRelPath() : "Unavailable");
        frameCount.setText(Integer.toString(animation.frameCount));
        Array<String> names = StudioAnimationAssets.orderedClipNames(animation);
        if (selectedClip == null || !names.contains(selectedClip, false)) {
            selectedClip = StudioAnimationAssets.initialClip(animation);
        }
        refreshClipBox(names);
        try {
            frames = frameLoader.apply(animation.sourceRelPath());
        } catch (RuntimeException unavailableSource) {
            frames = new Array<>();
        }
        rebuildPreview();
    }

    private void refreshClipBox(Array<String> names) {
        refreshingClips = true;
        try {
            clipBox.setItems(names.isEmpty() ? Array.with("All frames") : names);
            clipBox.setDisabled(names.isEmpty());
            clipBox.refresh();
        } finally {
            refreshingClips = false;
        }
    }

    private void selectClip(String clipName) {
        if (refreshingClips || animation == null || animation.clips == null
                || animation.clips.get(clipName) == null) return;
        selectedClip = clipName;
        rebuildPreview();
    }

    private void rebuildPreview() {
        if (preview != null) preview.stopPreview();
        previewHolder.clearChildren();
        preview = null;
        if (frames.isEmpty()) {
            previewHolder.add(new VisLabel("Preview unavailable"));
            return;
        }
        AnimationClipMeta clip = selectedClip != null ? animation.clips.get(selectedClip) : null;
        preview = new AnimatedThumbnail(frames, animation.fps,
                clip != null ? clip.start : 0,
                clip != null ? clip.end : frames.size - 1, clip != null && clip.flipX);
        preview.setScaling(Scaling.fit);
        preview.setName("animationAssetPreview");
        previewHolder.add(preview).grow();
        preview.startPreview();
    }

    private void stopPreview() {
        if (preview != null) preview.stopPreview();
        preview = null;
        previewHolder.clearChildren();
        // Release borrowed references only. StandaloneTextureCache owns the textures.
        frames = new Array<>();
    }

    @Override protected void setStage(Stage stage) {
        if (stage == null) stopPreview();
        super.setStage(stage);
        if (stage != null && animation != null && previewHolder.getChildren().isEmpty()) refresh();
    }
}
