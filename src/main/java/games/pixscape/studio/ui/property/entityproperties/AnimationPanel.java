package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AssetDisplayInfo;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.commands.EditAnimationCommand;
import games.pixscape.studio.history.commands.EditAnimationAssetFpsCommand;
import games.pixscape.studio.service.asset.StudioAnimationAssets;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.FloatField;

import java.util.function.Consumer;

public final class AnimationPanel extends CollapsibleWidget {
    private final EntityPropertiesContext ctx;
    private final VisTable root = new VisTable(true);
    // Raw VisUI controls are required for stable display-item-to-asset-ID mapping and icon styles.
    private final VisSelectBox<AnimationItem> animationBox = new VisSelectBox<>();
    private final VisSelectBox<String> clipBox = new VisSelectBox<>();
    private final Button addButton = new Button(VisUI.getSkin(), "add");
    private final Button deleteButton = new Button(VisUI.getSkin(), "delete");
    private final FloatField fpsField;
    private final VisCheckBox playingBox = new VisCheckBox("Playing");
    private final VisCheckBox loopBox = new VisCheckBox("Loop");

    private int entityId = -1;
    private boolean internalRefresh;

    public AnimationPanel(EntityPropertiesContext ctx) {
        this.ctx = ctx;
        setTable(root);
        root.left().top().pad(5);
        root.defaults().top().left();

        fpsField = new FloatField(
                ctx.world,
                e -> ctx.mAnim.get(e).fps,
                ctx.mAnim::has
        ).setDisplayDecimals(2);
        fpsField.setApplier(this::editActiveAnimationFps);

        animationBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalRefresh) return;
                AnimationItem selected = animationBox.getSelected();
                if (selected != null) switchAnimation(selected.assetId);
            }
        });
        clipBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalRefresh) return;
                String selected = clipBox.getSelected();
                if (selected == null) return;
                executeMutation(after -> {
                    after.currentClip = selected;
                    after.stateTime = 0f;
                    after.frame = -1;
                });
            }
        });
        addButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showAddMenu();
            }
        });
        deleteButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                deleteActiveAnimation();
            }
        });
        playingBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!internalRefresh) executeMutation(after -> after.playing = playingBox.isChecked());
            }
        });
        loopBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!internalRefresh) executeMutation(after -> after.loop = loopBox.isChecked());
            }
        });

        root.add(new VisLabel("Animation:")).width(CommonLayout.LABEL_WIDTH).left();
        root.add(animationBox).width(CommonLayout.FIELD_WIDTH).left();
        root.add(addButton).left();
        root.add(deleteButton).left().row();

        root.add(new VisLabel("Clip:")).width(CommonLayout.LABEL_WIDTH).left();
        root.add(clipBox).width(CommonLayout.FIELD_WIDTH).left().colspan(3).row();

        root.add(new VisLabel("FPS:")).width(CommonLayout.LABEL_WIDTH).left();
        root.add(fpsField).width(CommonLayout.FIELD_WIDTH).left().colspan(3).row();
        root.add(playingBox).left().colspan(4).row();
        root.add(loopBox).left().colspan(4).row();

        EventFlow.i().subscribe(EventFlow.AnimationChanged.class, event -> {
            if (event.entityId() == entityId) refresh();
        });
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
        fpsField.setEntityId(entityId);
        refresh();
    }

    public void refresh() {
        internalRefresh = true;
        try {
            AnimationComponent animation = animation();
            AssetRefComponent assetRef = assetRef();
            boolean valid = animation != null && assetRef != null;
            animationBox.setDisabled(!valid);
            clipBox.setDisabled(!valid);
            addButton.setDisabled(!valid);
            playingBox.setDisabled(!valid);
            loopBox.setDisabled(!valid);
            if (!valid) {
                animationBox.setItems();
                clipBox.setItems();
                deleteButton.setDisabled(true);
                playingBox.setChecked(false);
                loopBox.setChecked(false);
                return;
            }

            Array<AnimationItem> items = new Array<>();
            AnimationItem selectedItem = null;
            for (int i = 0; i < animation.animationAssetIds.size; i++) {
                int assetId = animation.animationAssetIds.get(i);
                AnimationAssetMeta meta = animationMeta(assetId);
                if (meta == null) continue;
                AnimationItem item = new AnimationItem(
                        AssetDisplayInfo.from(meta).displayName(), assetId);
                items.add(item);
                if (assetId == assetRef.assetId) selectedItem = item;
            }
            animationBox.setItems(items);
            animationBox.setSelected(selectedItem);
            deleteButton.setDisabled(items.size <= 1);
            refreshClipItems(animation, animationMeta(assetRef.assetId));
            playingBox.setChecked(animation.playing);
            loopBox.setChecked(animation.loop);
            fpsField.refreshFromModel();
        } finally {
            internalRefresh = false;
        }
    }

    private void refreshClipItems(AnimationComponent animation, AnimationAssetMeta meta) {
        Array<String> clips = StudioAnimationAssets.orderedClipNames(meta);
        clipBox.setItems(clips);
        clipBox.setSelected(clips.contains(animation.currentClip, false)
                ? animation.currentClip
                : null);
    }

    private void showAddMenu() {
        AnimationComponent animation = animation();
        if (animation == null || getStage() == null) return;
        PopupMenu menu = new PopupMenu();
        int eligible = 0;
        for (AnimationAssetMeta meta : ctx.animationAssets.get()) {
            if (meta == null || meta.id() <= 0 || animation.animationAssetIds.contains(meta.id())) continue;
            MenuItem item = new MenuItem(AssetDisplayInfo.from(meta).displayName());
            item.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    addAnimation(meta);
                }
            });
            menu.addItem(item);
            eligible++;
        }
        if (eligible == 0) {
            MenuItem empty = new MenuItem("No animations available");
            empty.setDisabled(true);
            menu.addItem(empty);
        }
        Vector2 position = addButton.localToStageCoordinates(new Vector2(0f, 0f));
        menu.showMenu(getStage(), position.x, position.y);
    }

    private void addAnimation(AnimationAssetMeta meta) {
        AnimationComponent animation = animation();
        if (animation == null || meta == null || animation.animationAssetIds.contains(meta.id())) return;
        executeMutation(after -> {
            after.animationAssetIds.add(meta.id());
            initializeFromAsset(after, meta, false);
        });
    }

    private void switchAnimation(int assetId) {
        AssetRefComponent assetRef = assetRef();
        AnimationAssetMeta meta = animationMeta(assetId);
        if (assetRef == null || assetRef.assetId == assetId || meta == null) return;
        executeMutation(after -> initializeFromAsset(after, meta, true));
    }

    private void deleteActiveAnimation() {
        AnimationComponent animation = animation();
        AssetRefComponent assetRef = assetRef();
        if (animation == null || assetRef == null || animation.animationAssetIds.size <= 1) return;
        int removedId = assetRef.assetId;
        executeMutation(after -> {
            after.animationAssetIds.removeValue(removedId);
            int replacementId = after.animationAssetIds.first();
            AnimationAssetMeta replacement = animationMeta(replacementId);
            if (replacement == null) {
                throw new IllegalStateException("Remaining Animation asset is missing: " + replacementId);
            }
            initializeFromAsset(after, replacement, true);
        });
    }

    private void initializeFromAsset(EditAnimationCommand.Snapshot after,
                                     AnimationAssetMeta meta,
                                     boolean keepValidCurrentClip) {
        after.activeAssetId = meta.id();
        String clip = keepValidCurrentClip
                && meta.clips != null
                && meta.clips.get(after.currentClip) != null
                ? after.currentClip
                : StudioAnimationAssets.initialClip(meta);
        if (clip == null) {
            throw new IllegalStateException("Animation asset has no authored clips: " + meta.id());
        }
        after.currentClip = clip;
        after.fps = meta.fps;
        after.stateTime = 0f;
        after.frame = -1;
    }

    private void editActiveAnimationFps(int targetEntityId, float value) {
        if (targetEntityId < 0
                || !ctx.world.getEntityManager().isActive(targetEntityId)) return;
        AnimationComponent animation = ctx.mAnim.getSafe(targetEntityId, null);
        AssetRefComponent assetRef = ctx.mSpriteSource.getSafe(targetEntityId, null);
        if (animation == null || assetRef == null) return;
        AnimationAssetMeta meta = animationMeta(assetRef.assetId);
        if (meta == null) return;

        float afterFps = Math.max(0.1f, value);
        ctx.history.execute(new EditAnimationAssetFpsCommand(
                ctx.world,
                ctx.history.historyIds(),
                targetEntityId,
                assetRef.assetId,
                meta.fps,
                afterFps,
                ctx.animationAssetAuthoringService,
                ctx.refreshAnimationPreview,
                ctx.markCurrentSceneSaveRequired
        ));
        refresh();
    }

    private void executeMutation(Consumer<EditAnimationCommand.Snapshot> mutation) {
        AnimationComponent animation = animation();
        AssetRefComponent assetRef = assetRef();
        if (animation == null || assetRef == null) return;
        EditAnimationCommand.Snapshot before = EditAnimationCommand.Snapshot.capture(animation, assetRef);
        EditAnimationCommand.Snapshot after = before.copy();
        mutation.accept(after);
        ctx.history.execute(new EditAnimationCommand(
                ctx.world,
                ctx.history.historyIds(),
                entityId,
                before,
                after,
                ctx.refreshAnimationPreview,
                ctx.markCurrentSceneSaveRequired
        ));
        refresh();
    }

    private AnimationComponent animation() {
        return entityId >= 0 && ctx.world.getEntityManager().isActive(entityId)
                ? ctx.mAnim.getSafe(entityId, null)
                : null;
    }

    private AssetRefComponent assetRef() {
        return entityId >= 0 && ctx.world.getEntityManager().isActive(entityId)
                ? ctx.mSpriteSource.getSafe(entityId, null)
                : null;
    }

    private AnimationAssetMeta animationMeta(int assetId) {
        return ctx.assetMetaLookup.apply(assetId) instanceof AnimationAssetMeta animation
                ? animation
                : null;
    }

    private record AnimationItem(String displayName, int assetId) {
        @Override
        public String toString() {
            return displayName;
        }
    }
}
