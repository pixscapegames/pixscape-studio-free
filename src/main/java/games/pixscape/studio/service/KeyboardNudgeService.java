package games.pixscape.studio.service;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.LongArray;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.GizmoTransformCommand;
import games.pixscape.studio.history.commands.TransformOp;

public final class KeyboardNudgeService {

    private static final float STEP = 1f;
    private static final float INITIAL_REPEAT_DELAY_SEC = 0.25f;
    private static final float REPEAT_INTERVAL_SEC = 0.025f;

    private final World world;
    private final HistoryManager historyManager;
    private final SelectionService selectionService;
    private final int sourceTag = EventFlow.tag(this);

    private final LongArray historyIds = new LongArray(false, 16);
    private final Array<GizmoTransformCommand.Snapshot> beforeSnapshots = new Array<>(false, 16);

    private int activeKeyMask = 0;
    private float repeatAccumulator = 0f;

    public KeyboardNudgeService(World world, HistoryManager historyManager, SelectionService selectionService) {
        this.world = world;
        this.historyManager = historyManager;
        this.selectionService = selectionService;
    }

    public boolean keyDown(int keycode) {
        int keyMask = arrowKeyMask(keycode);
        if (keyMask == 0) return false;

        IntArray selection = selectionService.getSelectionSnapshot();
        if (selection.size == 0 && activeKeyMask == 0) return false;

        boolean alreadyPressed = (activeKeyMask & keyMask) != 0;

        if (activeKeyMask == 0) {
            begin(selection);
            repeatAccumulator = -INITIAL_REPEAT_DELAY_SEC;
        }
        activeKeyMask |= keyMask;

        if (beforeSnapshots.size == 0) {
            activeKeyMask = 0;
            return false;
        }

        return alreadyPressed || apply(keyMask);
    }

    public boolean keyUp(int keycode) {
        int keyMask = arrowKeyMask(keycode);
        if (keyMask == 0 || activeKeyMask == 0) return false;

        activeKeyMask &= ~keyMask;
        if (activeKeyMask == 0) {
            commit();
        }
        return true;
    }

    public void update(float dt) {
        if (activeKeyMask == 0) return;

        int pressedMask = pressedArrowKeyMask();
        activeKeyMask &= pressedMask;
        if (activeKeyMask == 0) {
            commit();
            repeatAccumulator = 0f;
            return;
        }

        repeatAccumulator += Math.max(0f, dt);
        if (repeatAccumulator < 0f) {
            return;
        }

        while (repeatAccumulator >= REPEAT_INTERVAL_SEC) {
            repeatAccumulator -= REPEAT_INTERVAL_SEC;
            if (!apply(activeKeyMask)) {
                activeKeyMask = 0;
                repeatAccumulator = 0f;
                commit();
                return;
            }
        }
    }

    public boolean isActive() {
        return activeKeyMask != 0;
    }

    public static boolean isArrowKey(int keycode) {
        return keycode == Input.Keys.LEFT
                || keycode == Input.Keys.RIGHT
                || keycode == Input.Keys.UP
                || keycode == Input.Keys.DOWN;
    }

    private void begin(IntArray selection) {
        historyIds.clear();
        beforeSnapshots.clear();

        ComponentMapper<TransformComponent> mTransform = world.getMapper(TransformComponent.class);
        for (int i = 0; i < selection.size; i++) {
            int entityId = selection.get(i);
            if (!world.getEntityManager().isActive(entityId)) continue;

            TransformComponent transform = mTransform.getSafe(entityId, null);
            if (transform == null) continue;

            long historyId = historyManager.historyIds().ensureForEntity(entityId);
            if (historyId <= 0L) continue;

            historyIds.add(historyId);
            beforeSnapshots.add(GizmoTransformCommand.Snapshot.of(transform));
        }
    }

    private boolean apply(int keyMask) {
        float dx = 0f;
        float dy = 0f;
        if ((keyMask & arrowKeyMask(Input.Keys.LEFT)) != 0) dx -= STEP;
        if ((keyMask & arrowKeyMask(Input.Keys.RIGHT)) != 0) dx += STEP;
        if ((keyMask & arrowKeyMask(Input.Keys.UP)) != 0) dy += STEP;
        if ((keyMask & arrowKeyMask(Input.Keys.DOWN)) != 0) dy -= STEP;
        if (dx == 0f && dy == 0f) return true;

        boolean moved = false;
        ComponentMapper<TransformComponent> mTransform = world.getMapper(TransformComponent.class);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);

        for (int i = 0; i < historyIds.size; i++) {
            long historyId = historyIds.get(i);
            int entityId = historyManager.historyIds().entityOfHistoryId(historyId);
            if (!world.getEntityManager().isActive(entityId)) continue;

            TransformComponent transform = mTransform.getSafe(entityId, null);
            if (transform == null) continue;

            transform.x += dx;
            transform.y += dy;
            moved = true;

            if (dirty != null) {
                dirty.geometry(entityId, GeometryDirty.POSITION);
            }
            EventFlow.i().publish(new EventFlow.EntityChanged(entityId, TransformOp.MOVE, sourceTag));
        }

        return moved;
    }

    private void commit() {
        GizmoTransformCommand cmd =
                new GizmoTransformCommand(world, historyManager.historyIds(), TransformOp.MOVE);

        ComponentMapper<TransformComponent> mTransform = world.getMapper(TransformComponent.class);
        for (int i = 0; i < historyIds.size; i++) {
            long historyId = historyIds.get(i);
            int entityId = historyManager.historyIds().entityOfHistoryId(historyId);
            if (entityId < 0 || !world.getEntityManager().isActive(entityId)) continue;

            TransformComponent transform = mTransform.getSafe(entityId, null);
            if (transform == null) continue;

            cmd.addEntry(
                    historyId,
                    beforeSnapshots.get(i),
                    GizmoTransformCommand.Snapshot.of(transform)
            );
        }

        historyIds.clear();
        beforeSnapshots.clear();

        if (!cmd.isNoop()) {
            historyManager.execute(cmd);
        }
    }

    private static int arrowKeyMask(int keycode) {
        return switch (keycode) {
            case Input.Keys.LEFT -> 1;
            case Input.Keys.RIGHT -> 2;
            case Input.Keys.UP -> 4;
            case Input.Keys.DOWN -> 8;
            default -> 0;
        };
    }

    private static int pressedArrowKeyMask() {
        int mask = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) mask |= arrowKeyMask(Input.Keys.LEFT);
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) mask |= arrowKeyMask(Input.Keys.RIGHT);
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) mask |= arrowKeyMask(Input.Keys.UP);
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) mask |= arrowKeyMask(Input.Keys.DOWN);
        return mask;
    }
}
