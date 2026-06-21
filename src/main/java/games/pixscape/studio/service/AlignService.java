package games.pixscape.studio.service;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.CompositeCommand;
import games.pixscape.studio.history.commands.EditTransformCommand;
import games.pixscape.studio.history.commands.TransformOp;
import games.pixscape.studio.ui.main.WorldCanvas;

import java.util.ArrayList;

public final class AlignService {

    private final World world;
    private final SelectionService selectionService;
    private final HistoryManager historyManager;
    private final ComponentMapper<TransformComponent> mTransform;
    private final ComponentMapper<DimensionsComponent> mDimensions;

    public AlignService(WorldCanvas canvas) {
        this.world = canvas.getEcsWorld();
        this.selectionService = canvas.getSelectionService();
        this.historyManager = canvas.getHistoryManager();
        this.mTransform = world.getMapper(TransformComponent.class);
        this.mDimensions = world.getMapper(DimensionsComponent.class);
    }

    public void alignLeft() {
        IntArray selection = selectionService.getSelectionSnapshot();
        if (selection.size < 2) {
            return;
        }

        int referenceEntityId = selectionService.getValidFirstSelectedEntityId();
        if (referenceEntityId < 0) {
            return;
        }

        Bounds referenceBounds = computeBounds(referenceEntityId);
        if (referenceBounds == null) {
            return;
        }

        ArrayList<Command> commands = new ArrayList<>();

        for (int i = 0; i < selection.size; i++) {
            int entityId = selection.get(i);

            if (entityId == referenceEntityId) {
                continue;
            }

            TransformComponent transform = mTransform.getSafe(entityId, null);
            if (transform == null) {
                continue;
            }

            Bounds bounds = computeBounds(entityId);
            if (bounds == null) {
                continue;
            }

            float deltaX = referenceBounds.left() - bounds.left();
            if (Math.abs(deltaX) < 0.0001f) {
                continue;
            }

            EditTransformCommand.Snapshot before = EditTransformCommand.Snapshot.capture(transform);
            EditTransformCommand.Snapshot after = before.withX(transform.x + deltaX);

            EditTransformCommand cmd = new EditTransformCommand(
                    world,
                    historyManager.historyIds(),
                    entityId,
                    TransformOp.MOVE,
                    before,
                    after
            );

            if (!cmd.isNoop()) {
                commands.add(cmd);
            }
        }

        if (!commands.isEmpty()) {
            historyManager.execute(new CompositeCommand("Align Left", commands));
        }
    }


    public void alignRight() {
        IntArray selection = selectionService.getSelectionSnapshot();
        if (selection.size < 2) {
            return;
        }

        int referenceEntityId = selectionService.getValidFirstSelectedEntityId();
        if (referenceEntityId < 0) {
            return;
        }

        Bounds referenceBounds = computeBounds(referenceEntityId);
        if (referenceBounds == null) {
            return;
        }

        ArrayList<Command> commands = new ArrayList<>();

        for (int i = 0; i < selection.size; i++) {
            int entityId = selection.get(i);

            if (entityId == referenceEntityId) {
                continue;
            }

            TransformComponent transform = mTransform.getSafe(entityId, null);
            if (transform == null) {
                continue;
            }

            Bounds bounds = computeBounds(entityId);
            if (bounds == null) {
                continue;
            }

            float deltaX = referenceBounds.right() - bounds.right();
            if (Math.abs(deltaX) < 0.0001f) {
                continue;
            }

            EditTransformCommand.Snapshot before = EditTransformCommand.Snapshot.capture(transform);
            EditTransformCommand.Snapshot after = before.withX(transform.x + deltaX);

            EditTransformCommand cmd = new EditTransformCommand(
                    world,
                    historyManager.historyIds(),
                    entityId,
                    TransformOp.MOVE,
                    before,
                    after
            );

            if (!cmd.isNoop()) {
                commands.add(cmd);
            }
        }

        if (!commands.isEmpty()) {
            historyManager.execute(new CompositeCommand("Align Right", commands));
        }
    }

    public void alignTop() {
        IntArray selection = selectionService.getSelectionSnapshot();
        if (selection.size < 2) {
            return;
        }

        int referenceEntityId = selectionService.getValidFirstSelectedEntityId();
        if (referenceEntityId < 0) {
            return;
        }

        Bounds referenceBounds = computeBounds(referenceEntityId);
        if (referenceBounds == null) {
            return;
        }

        ArrayList<Command> commands = new ArrayList<>();

        for (int i = 0; i < selection.size; i++) {
            int entityId = selection.get(i);

            if (entityId == referenceEntityId) {
                continue;
            }

            TransformComponent transform = mTransform.getSafe(entityId, null);
            if (transform == null) {
                continue;
            }

            Bounds bounds = computeBounds(entityId);
            if (bounds == null) {
                continue;
            }

            float deltaY = referenceBounds.top() - bounds.top();
            if (Math.abs(deltaY) < 0.0001f) {
                continue;
            }

            EditTransformCommand.Snapshot before = EditTransformCommand.Snapshot.capture(transform);
            EditTransformCommand.Snapshot after = before.withY(transform.y + deltaY);

            EditTransformCommand cmd = new EditTransformCommand(
                    world,
                    historyManager.historyIds(),
                    entityId,
                    TransformOp.MOVE,
                    before,
                    after
            );

            if (!cmd.isNoop()) {
                commands.add(cmd);
            }
        }

        if (!commands.isEmpty()) {
            historyManager.execute(new CompositeCommand("Align Top", commands));
        }
    }

    public void alignBottom() {
        IntArray selection = selectionService.getSelectionSnapshot();
        if (selection.size < 2) {
            return;
        }

        int referenceEntityId = selectionService.getValidFirstSelectedEntityId();
        if (referenceEntityId < 0) {
            return;
        }

        Bounds referenceBounds = computeBounds(referenceEntityId);
        if (referenceBounds == null) {
            return;
        }

        ArrayList<Command> commands = new ArrayList<>();

        for (int i = 0; i < selection.size; i++) {
            int entityId = selection.get(i);

            if (entityId == referenceEntityId) {
                continue;
            }

            TransformComponent transform = mTransform.getSafe(entityId, null);
            if (transform == null) {
                continue;
            }

            Bounds bounds = computeBounds(entityId);
            if (bounds == null) {
                continue;
            }

            float deltaY = referenceBounds.bottom() - bounds.bottom();
            if (Math.abs(deltaY) < 0.0001f) {
                continue;
            }

            EditTransformCommand.Snapshot before = EditTransformCommand.Snapshot.capture(transform);
            EditTransformCommand.Snapshot after = before.withY(transform.y + deltaY);

            EditTransformCommand cmd = new EditTransformCommand(
                    world,
                    historyManager.historyIds(),
                    entityId,
                    TransformOp.MOVE,
                    before,
                    after
            );

            if (!cmd.isNoop()) {
                commands.add(cmd);
            }
        }

        if (!commands.isEmpty()) {
            historyManager.execute(new CompositeCommand("Align Bottom", commands));
        }
    }

    public void centerHorizontal() {
        IntArray selection = selectionService.getSelectionSnapshot();
        if (selection.size < 2) {
            return;
        }

        int referenceEntityId = selectionService.getValidFirstSelectedEntityId();
        if (referenceEntityId < 0) {
            return;
        }

        Bounds referenceBounds = computeBounds(referenceEntityId);
        if (referenceBounds == null) {
            return;
        }

        ArrayList<Command> commands = new ArrayList<>();

        for (int i = 0; i < selection.size; i++) {
            int entityId = selection.get(i);
            if (entityId == referenceEntityId) {
                continue;
            }

            TransformComponent transform = mTransform.getSafe(entityId, null);
            Bounds bounds = computeBounds(entityId);
            if (transform == null || bounds == null) {
                continue;
            }

            float deltaX = referenceBounds.centerX() - bounds.centerX();
            if (Math.abs(deltaX) < 0.0001f) {
                continue;
            }

            Command cmd = buildMoveCommand(entityId, transform.x + deltaX, transform.y);
            if (cmd != null) {
                commands.add(cmd);
            }
        }

        if (!commands.isEmpty()) {
            historyManager.execute(new CompositeCommand("Center Horizontally", commands));
        }
    }

    public void centerVertical() {
        IntArray selection = selectionService.getSelectionSnapshot();
        if (selection.size < 2) {
            return;
        }

        int referenceEntityId = selectionService.getValidFirstSelectedEntityId();
        if (referenceEntityId < 0) {
            return;
        }

        Bounds referenceBounds = computeBounds(referenceEntityId);
        if (referenceBounds == null) {
            return;
        }

        ArrayList<Command> commands = new ArrayList<>();

        for (int i = 0; i < selection.size; i++) {
            int entityId = selection.get(i);
            if (entityId == referenceEntityId) {
                continue;
            }

            TransformComponent transform = mTransform.getSafe(entityId, null);
            Bounds bounds = computeBounds(entityId);
            if (transform == null || bounds == null) {
                continue;
            }

            float deltaY = referenceBounds.centerY() - bounds.centerY();
            if (Math.abs(deltaY) < 0.0001f) {
                continue;
            }

            Command cmd = buildMoveCommand(entityId, transform.x, transform.y + deltaY);
            if (cmd != null) {
                commands.add(cmd);
            }
        }

        if (!commands.isEmpty()) {
            historyManager.execute(new CompositeCommand("Center Vertically", commands));
        }
    }

    public void packHorizontal() {
        IntArray selection = selectionService.getSelectionSnapshot();
        if (selection.size < 2) {
            return;
        }

        java.util.ArrayList<Integer> entities = new java.util.ArrayList<>();
        for (int i = 0; i < selection.size; i++) {
            int entityId = selection.get(i);
            if (computeBounds(entityId) != null && mTransform.getSafe(entityId, null) != null) {
                entities.add(entityId);
            }
        }

        if (entities.size() < 2) {
            return;
        }

        entities.sort((a, b) -> {
            Bounds ba = computeBounds(a);
            Bounds bb = computeBounds(b);
            assert ba != null;
            assert bb != null;
            int cmp = Float.compare(ba.left(), bb.left());
            return cmp != 0 ? cmp : Integer.compare(a, b);
        });

        java.util.ArrayList<Command> commands = new java.util.ArrayList<>();

        Bounds firstBounds = computeBounds(entities.getFirst());
        if (firstBounds == null) {
            return;
        }

        float cursor = firstBounds.left();

        for (int entityId : entities) {
            TransformComponent transform = mTransform.getSafe(entityId, null);
            Bounds bounds = computeBounds(entityId);
            if (transform == null || bounds == null) {
                continue;
            }

            float deltaX = cursor - bounds.left();
            float newX = transform.x + deltaX;

            Command cmd = buildMoveCommand(entityId, newX, transform.y);
            if (cmd != null) {
                commands.add(cmd);
            }

            cursor += (bounds.right() - bounds.left());
        }

        if (!commands.isEmpty()) {
            historyManager.execute(new CompositeCommand("Pack Horizontally", commands));
        }
    }

    public void packVertical() {
        IntArray selection = selectionService.getSelectionSnapshot();
        if (selection.size < 2) {
            return;
        }

        java.util.ArrayList<Integer> entities = new java.util.ArrayList<>();
        for (int i = 0; i < selection.size; i++) {
            int entityId = selection.get(i);
            if (computeBounds(entityId) != null && mTransform.getSafe(entityId, null) != null) {
                entities.add(entityId);
            }
        }

        if (entities.size() < 2) {
            return;
        }

        entities.sort((a, b) -> {
            Bounds ba = computeBounds(a);
            Bounds bb = computeBounds(b);
            assert ba != null;
            assert bb != null;
            int cmp = Float.compare(ba.bottom(), bb.bottom());
            return cmp != 0 ? cmp : Integer.compare(a, b);
        });

        java.util.ArrayList<Command> commands = new java.util.ArrayList<>();

        Bounds firstBounds = computeBounds(entities.getFirst());
        if (firstBounds == null) {
            return;
        }

        float cursor = firstBounds.bottom();

        for (int entityId : entities) {
            TransformComponent transform = mTransform.getSafe(entityId, null);
            Bounds bounds = computeBounds(entityId);
            if (transform == null || bounds == null) {
                continue;
            }

            float deltaY = cursor - bounds.bottom();
            float newY = transform.y + deltaY;

            Command cmd = buildMoveCommand(entityId, transform.x, newY);
            if (cmd != null) {
                commands.add(cmd);
            }

            cursor += (bounds.top() - bounds.bottom());
        }

        if (!commands.isEmpty()) {
            historyManager.execute(new CompositeCommand("Pack Vertically", commands));
        }
    }

    private float snap(float value) {
        return Math.round(value);
    }

    private Command buildMoveCommand(int entityId, float newX, float newY) {
        TransformComponent transform = mTransform.getSafe(entityId, null);
        if (transform == null) {
            return null;
        }

        EditTransformCommand.Snapshot before = EditTransformCommand.Snapshot.capture(transform);
        EditTransformCommand.Snapshot after = before
                .withX(newX)
                .withY(newY);

        EditTransformCommand cmd = new EditTransformCommand(
                world,
                historyManager.historyIds(),
                entityId,
                TransformOp.MOVE,
                before,
                after
        );

        return cmd.isNoop() ? null : cmd;
    }

    private Bounds computeBounds(int entityId) {
        TransformComponent t = mTransform.getSafe(entityId, null);
        DimensionsComponent d = mDimensions.getSafe(entityId, null);

        if (t == null || d == null) return null;

        float localX0 = -t.originX;
        float localY0 = -t.originY;
        float localX1 = d.width - t.originX;
        float localY1 = d.height - t.originY;

        float sx0 = localX0 * t.scaleX;
        float sy0 = localY0 * t.scaleY;
        float sx1 = localX1 * t.scaleX;
        float sy1 = localY1 * t.scaleY;

        float cos = (float) Math.cos(t.rotationRad);
        float sin = (float) Math.sin(t.rotationRad);

        float x1 = t.x + sx0 * cos - sy0 * sin;
        float y1 = t.y + sx0 * sin + sy0 * cos;

        float x2 = t.x + sx1 * cos - sy0 * sin;
        float y2 = t.y + sx1 * sin + sy0 * cos;

        float x3 = t.x + sx1 * cos - sy1 * sin;
        float y3 = t.y + sx1 * sin + sy1 * cos;

        float x4 = t.x + sx0 * cos - sy1 * sin;
        float y4 = t.y + sx0 * sin + sy1 * cos;

        float left = Math.min(Math.min(x1, x2), Math.min(x3, x4));
        float right = Math.max(Math.max(x1, x2), Math.max(x3, x4));
        float bottom = Math.min(Math.min(y1, y2), Math.min(y3, y4));
        float top = Math.max(Math.max(y1, y2), Math.max(y3, y4));

        return new Bounds(left, right, bottom, top);
    }

    private record Bounds(float left, float right, float bottom, float top) {
        float centerX() {
            return (left + right) * 0.5f;
        }

        float centerY() {
            return (bottom + top) * 0.5f;
        }
    }
}
