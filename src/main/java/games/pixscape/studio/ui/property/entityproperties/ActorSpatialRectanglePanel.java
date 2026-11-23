package games.pixscape.studio.ui.property.entityproperties;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.studio.history.commands.EditActorSpatialRectangleCommand;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.FloatField;

import java.util.Objects;

/**
 * Selected-visual controls use the same field widths and indentation as the Footprint group.
 */
final class ActorSpatialRectanglePanel {
    private final EntityPropertiesContext ctx;
    private final FloatField[] fields = new FloatField[6];
    private final float[] displayed = new float[6];
    private int entity = -1, asset;
    private String clip;

    ActorSpatialRectanglePanel(EntityPropertiesContext ctx, VisTable table) {
        this.ctx = ctx;
        SpatialActorPanel.addGroupTitle(table, "Occlusion rectangle", "Selected image / local pixels");
        String[] names = {"Width (px):", "Height (px):", "X (px):", "Y (px):", "X (px):", "Y (px):"};
        for (int i = 0; i < fields.length; i++) {
            if (i == 4) {
                SpatialActorPanel.addGroupTitle(table, "Shadow pivot", "Selected representation / footprint owner origin (projected px)");
            }
            final int property = i;
            FloatField field = new FloatField(ctx.world, e -> readPixels(e, property), e -> rectangle(e) != null).setDisplayDecimals(2);
            field.setName("actor-spatial-rectangle-" + i);
            field.setKeepInvalidInput(true);
            field.setValidator(FloatField.finiteValidator(i < 2));
            field.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
                @Override
                public boolean keyDown(com.badlogic.gdx.scenes.scene2d.InputEvent event, int key) {
                    if (key != com.badlogic.gdx.Input.Keys.ESCAPE) {
                        return false;
                    }
                    field.refreshFromModel();
                    event.stop();
                    return true;
                }
            });
            field.setApplier((e, value) -> {
                ActorSpatialRectangleData current = rectangle(e);
                if (current == null || !Float.isFinite(value) || property < 2 && value <= 0) {
                    return;
                }
                ActorSpatialRectangleData next = current.copy();
                write(next, property, value / unitScale(e, property));
                ctx.history.execute(new EditActorSpatialRectangleCommand(ctx.world, ctx.history.historyIds(), e, asset, clip, next, ctx.markCurrentSceneSaveRequired));
            });
            fields[i] = field;
            table.add(new VisLabel(names[i])).width(CommonLayout.LABEL_WIDTH).padLeft(CommonLayout.PAD_LEFT_SUBMENU)
                    .expandX().left().padBottom(i == 3 ? 15 : 1);
            table.add(field).width(CommonLayout.FIELD_WIDTH).padBottom(i == 3 ? 15 : 1).row();
        }
    }

    private ActorSpatialRectangleData rectangle(int e) {
        SpatialRepresentationComponent s = e < 0 ? null : ctx.mSpatialRepresentation.getSafe(e, null);
        var size = e < 0 ? null : ctx.mDimensions.getSafe(e, null);
        if (size == null || size.width <= 0 || size.height <= 0) {
            return null;
        }
        return s == null || s.rectangles == null ? null : s.rectangles.get(asset, clip);
    }

    void refresh(int e) {
        entity = e;
        AssetRefComponent a = e < 0 ? null : ctx.mSpriteSource.getSafe(e, null);
        AnimationComponent c = e < 0 ? null : ctx.mAnim.getSafe(e, null);
        asset = a == null ? 0 : a.assetId;
        clip = c == null ? null : c.currentClip;
        for (int i = 0; i < fields.length; i++) {
            fields[i].setEntityId(e);
            displayed[i] = readPixels(e, i);
        }
    }

    void refreshCurrentRepresentation() {
        if (entity < 0 || !ctx.world.getEntityManager().isActive(entity)) {
            return;
        }
        AssetRefComponent a = ctx.mSpriteSource.getSafe(entity, null);
        AnimationComponent c = ctx.mAnim.getSafe(entity, null);
        if (asset != (a == null ? 0 : a.assetId) || !Objects.equals(clip, c == null ? null : c.currentClip)) {
            refresh(entity);
        }
        // Model changes during a canvas gesture are reflected without rewriting a focused field.
        for (int i = 0; i < fields.length; i++) {
            if (!fields[i].hasKeyboardFocus() && displayed[i] != readPixels(entity, i)) {
                fields[i].refreshFromModel();
                displayed[i] = readPixels(entity, i);
            }
        }
    }

    private float unitScale(int e, int property) {
        if (property >= 4) {
            return 1f;
        }
        var size = e < 0 ? null : ctx.mDimensions.getSafe(e, null);
        return size == null ? 1f : property == 0 || property == 2 ? size.width : size.height;
    }

    private float readPixels(int e, int property) {
        return read(rectangle(e), property) * unitScale(e, property);
    }

    static float read(ActorSpatialRectangleData v, int p) {
        if (v == null) {
            return 0;
        }
        return switch (p) {
            case 0 -> v.width;
            case 1 -> v.height;
            case 2 -> v.x;
            case 3 -> v.y;
            case 4 -> v.pivotX;
            default -> v.pivotY;
        };
    }

    static void write(ActorSpatialRectangleData v, int p, float n) {
        switch (p) {
            case 0 -> v.width = n;
            case 1 -> v.height = n;
            case 2 -> v.x = n;
            case 3 -> v.y = n;
            case 4 -> v.pivotX = n;
            default -> v.pivotY = n;
        }
    }
}
