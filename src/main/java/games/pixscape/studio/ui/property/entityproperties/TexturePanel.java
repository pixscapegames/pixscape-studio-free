package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.kotcrab.vis.ui.widget.VisImage;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.studio.asset.AssetDisplayInfo;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.history.commands.ReplaceSpriteTextureCommand;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.ui.modal.Dialogs;
import games.pixscape.studio.ui.modal.StudioDialog;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.SimpleSelectBox;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Required sprite content; collapsing the section never removes its data. */
public final class TexturePanel {
    private final EntityPropertiesContext ctx;
    private final StudioAssetVisualResolver resolver;
    private final Supplier<Array<AssetMeta>> images;
    private final IntConsumer onBound;
    private final CollapsibleVisTable content = new CollapsibleVisTable(true);
    private final VisLabel name = new VisLabel();
    private final TextureRegion previewRegion = new TextureRegion();
    private final TextureRegionDrawable previewDrawable = new TextureRegionDrawable();
    // VisImage provides native fitting without a second texture loader or ownership of shared pixels.
    private final VisImage preview = new VisImage() {
        @Override
        public void act(float delta) {
            super.act(delta);
            refreshPreview();
        }
    };
    private int entity = -1;
    private boolean refreshRequired;

    public TexturePanel(EntityPropertiesContext ctx, StudioAssetVisualResolver resolver,
                        Supplier<Array<AssetMeta>> images, IntConsumer onBound) {
        this.ctx = ctx;
        this.resolver = resolver;
        this.images = images;
        this.onBound = onBound;
        preview.setScaling(Scaling.fit);
        preview.setName("sprite-texture-preview");
        name.setEllipsis(true);
        VisTextButton choose = new VisTextButton("Choose image…");
        choose.setName("sprite-texture-choose");
        choose.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showSelector();
            }
        });
        content.content().defaults().pad(5).left();
        content.content().add(name).growX().row();
        content.content().add(preview).growX().height(72).row();
        content.content().add(choose).left().row();
    }

    public CollapsibleVisTable content() {
        return content;
    }

    public boolean isApplicable(int entity) {
        return ReplaceSpriteTextureCommand.isSprite(ctx.world, entity, ctx.assetMetaLookup);
    }

    public void setEntityId(int entity) {
        this.entity = entity;
        refreshRequired = true;
    }

    public void requestRefresh() {
        refreshRequired = true;
    }

    private void refreshPreview() {
        if (refreshRequired) {
            refreshRequired = false;
            refreshName();
        }
        updateBorrowedPreview();
    }

    private void refreshName() {
        AssetRefComponent source = entity < 0 ? null : ctx.mSpriteSource.getSafe(entity, null);
        AssetMeta meta = source == null ? null : ctx.assetMetaLookup.apply(source.assetId);
        name.setText(meta == null ? "Image unavailable" : AssetDisplayInfo.from(meta).displayName());
    }

    private void updateBorrowedPreview() {
        RenderMaterialComponent material = entity < 0 ? null : ctx.mMat.getSafe(entity, null);
        TextureRegionComponent region = entity < 0 ? null : ctx.mTexRegion.getSafe(entity, null);
        Texture texture = material == null ? null : TextureRegistry.getByHandle(material.textureHandle);
        if (texture == null || region == null || !region.valid) {
            preview.setDrawable((com.badlogic.gdx.scenes.scene2d.utils.Drawable) null);
            return;
        }
        // Repack may replace the borrowed texture/UVs without changing the authored asset reference.
        if (preview.getDrawable() == null || previewRegion.getTexture() != texture
                || previewRegion.getU() != region.u1 || previewRegion.getV() != region.v1
                || previewRegion.getU2() != region.u2 || previewRegion.getV2() != region.v2) {
            previewRegion.setTexture(texture);
            previewRegion.setRegion(region.u1, region.v1, region.u2, region.v2);
            previewDrawable.setRegion(previewRegion);
            preview.setDrawable(previewDrawable);
            preview.invalidateHierarchy();
        }
    }

    private void showSelector() {
        if (!isApplicable(entity)) {
            return;
        }
        int selectedEntity = entity;
        long revision = ctx.history.getMutationRevision();
        SimpleSelectBox<ImageItem> choices = new SimpleSelectBox<>();
        Array<ImageItem> items = new Array<>();
        ImageItem current = null;
        int currentId = ctx.mSpriteSource.get(entity).assetId;
        for (AssetMeta image : images.get()) {
            ImageItem item = new ImageItem(image.id(), AssetDisplayInfo.from(image).displayName());
            items.add(item);
            if (image.id() == currentId) {
                current = item;
            }
        }
        choices.setItems(items);
        if (current != null) {
            choices.setSelected(current);
        }
        StudioDialog dialog = new StudioDialog("Choose sprite image") {
            @Override
            protected void result(Object result) {
                if (!Boolean.TRUE.equals(result) || choices.getSelected() == null
                        || ctx.history.getMutationRevision() != revision || entity != selectedEntity) {
                    return;
                }
                try {
                    ctx.history.execute(new ReplaceSpriteTextureCommand(ctx.world, ctx.history.historyIds(),
                            selectedEntity, choices.getSelected().id, resolver, ctx.assetMetaLookup, onBound));
                } catch (IllegalArgumentException failure) {
                    Dialogs.showErrorDialog(getStage(), failure.getMessage());
                }
            }
        };
        dialog.getContentTable().add(choices).width(300).pad(10).row();
        dialog.getContentTable().add(new VisLabel("Uses the new image's native dimensions.")).pad(10).row();
        dialog.button("Cancel", false);
        VisTextButton replace = new VisTextButton("Replace");
        replace.setDisabled(items.isEmpty());
        dialog.button(replace, true);
        dialog.closeOnEscape();
        dialog.show(content.getStage());
    }

    private record ImageItem(int id, String name) {
        @Override
        public String toString() {
            return name + " (#" + id + ")";
        }
    }
}
