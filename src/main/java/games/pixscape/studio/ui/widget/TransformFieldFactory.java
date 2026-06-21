package games.pixscape.studio.ui.widget;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.studio.helper.GeometryHelper;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.EditTransformCommand;
import games.pixscape.studio.history.commands.TransformOp;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Builds history-aware fields for TransformComponent (X/Y/Rotation/Scale/Origin).
 * - Toute mutation utilisateur passe par HistoryManager.
 * - Rebind : call setEntityId(eid) on the returned fields.
 */
public final class TransformFieldFactory {

    private final World world;
    private final HistoryManager history;
    private final ComponentMapper<TransformComponent> mT;
    private final ComponentMapper<AssetRefComponent> mSpriteSource;

    public TransformFieldFactory(World world, HistoryManager history) {
        this.world = Objects.requireNonNull(world, "world");
        this.history = Objects.requireNonNull(history, "history");
        this.mT = world.getMapper(TransformComponent.class);
        this.mSpriteSource = world.getMapper(AssetRefComponent.class);
    }

    public FloatField posX() {
        FloatField f = new FloatField(world, e -> {
            TransformComponent t = mT.getSafe(e, null);
            if (t == null) return 0f;
            return shouldUseSpritePosition(e) ? (t.x - t.originX) : t.x;
        }, mT::has);

        f.setApplier((eid, v) -> submitTransformEdit(
                eid,
                TransformOp.MOVE,
                before -> before.withX(shouldUseSpritePosition(eid) ? (v + before.originX()) : v)
        ));
        return f;
    }

    public FloatField posX(int entityId) {
        FloatField f = posX();
        f.setEntityId(entityId);
        return f;
    }

    public FloatField posY() {
        FloatField f = new FloatField(world, e -> {
            TransformComponent t = mT.getSafe(e, null);
            if (t == null) return 0f;
            return shouldUseSpritePosition(e) ? (t.y - t.originY) : t.y;
        }, mT::has);

        f.setApplier((eid, v) -> submitTransformEdit(
                eid,
                TransformOp.MOVE,
                before -> before.withY(shouldUseSpritePosition(eid) ? (v + before.originY()) : v)
        ));
        return f;
    }

    public FloatField posY(int entityId) {
        FloatField f = posY();
        f.setEntityId(entityId);
        return f;
    }

    public FloatField rotation() {
        FloatField f = new FloatField(
                world,
                e -> {
                    TransformComponent t = mT.getSafe(e, null);
                    return t != null ? GeometryHelper.rotationRadToEditorDeg(t.rotationRad) : 0f;
                },
                mT::has
        );

        f.setApplier((eid, v) -> submitTransformEdit(
                eid,
                TransformOp.ROTATE,
                before -> before.withRotationRad(GeometryHelper.editorDegToRotationRad(v))
        ));
        return f;
    }

    public FloatField rotation(int entityId) {
        FloatField f = rotation();
        f.setEntityId(entityId);
        return f;
    }

    public FloatField scaleX() {
        FloatField f = new FloatField(world, e -> {
            TransformComponent t = mT.getSafe(e, null);
            return t != null ? t.scaleX : 1f;
        }, mT::has);

        f.setApplier((eid, v) -> submitTransformEdit(
                eid,
                TransformOp.SCALE,
                before -> before.withScaleX(v)
        ));
        return f;
    }

    public FloatField scaleX(int entityId) {
        FloatField f = scaleX();
        f.setEntityId(entityId);
        return f;
    }

    public FloatField scaleY() {
        FloatField f = new FloatField(world, e -> {
            TransformComponent t = mT.getSafe(e, null);
            return t != null ? t.scaleY : 1f;
        }, mT::has);

        f.setApplier((eid, v) -> submitTransformEdit(
                eid,
                TransformOp.SCALE,
                before -> before.withScaleY(v)
        ));
        return f;
    }

    public FloatField scaleY(int entityId) {
        FloatField f = scaleY();
        f.setEntityId(entityId);
        return f;
    }

    public FloatField originX() {
        FloatField f = new FloatField(world, e -> {
            TransformComponent t = mT.getSafe(e, null);
            return t != null ? t.originX : 0f;
        }, mT::has);

        f.setApplier((eid, v) -> submitTransformEdit(
                eid,
                TransformOp.ORIGIN,
                before -> before.withOriginX(v)
        ));
        return f;
    }

    public FloatField originX(int entityId) {
        FloatField f = originX();
        f.setEntityId(entityId);
        return f;
    }

    public FloatField originY() {
        FloatField f = new FloatField(world, e -> {
            TransformComponent t = mT.getSafe(e, null);
            return t != null ? t.originY : 0f;
        }, mT::has);

        f.setApplier((eid, v) -> submitTransformEdit(
                eid,
                TransformOp.ORIGIN,
                before -> before.withOriginY(v)
        ));
        return f;
    }

    public FloatField originY(int entityId) {
        FloatField f = originY();
        f.setEntityId(entityId);
        return f;
    }

    private void submitTransformEdit(int entityId,
                                     TransformOp op,
                                     UnaryOperator<EditTransformCommand.Snapshot> edit) {
        if (entityId < 0 || !mT.has(entityId) || edit == null) return;

        TransformComponent transform = mT.getSafe(entityId, null);
        if (transform == null) return;

        EditTransformCommand.Snapshot before = EditTransformCommand.Snapshot.capture(transform);
        if (before == null) return;

        EditTransformCommand.Snapshot after = edit.apply(before);
        if (after == null) return;

        Command command = new EditTransformCommand(
                world,
                history.historyIds(),
                entityId,
                op,
                before,
                after
        );

        if (command instanceof HistoryManager.SupportsNoop supportsNoop && supportsNoop.isNoop()) {
            return;
        }

        history.execute(command);
    }

    private boolean shouldUseSpritePosition(int entityId) {
        return mSpriteSource != null && mSpriteSource.has(entityId);
    }

    private static float beforeOriginX(EditTransformCommand.Snapshot snapshot) {
        TransformComponent temp = new TransformComponent();
        snapshot.apply(temp);
        return temp.originX;
    }

    private static float beforeOriginY(EditTransformCommand.Snapshot snapshot) {
        TransformComponent temp = new TransformComponent();
        snapshot.apply(temp);
        return temp.originY;
    }
}