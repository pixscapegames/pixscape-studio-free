package games.pixscape.studio.ui.tree;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Routes right clicks on supported Items tree rows to their context menu.
 *
 * <p>The menu is intentionally shown on touch-up. A VisUI {@code PopupMenu}
 * installs its outside-click listener when it is shown; showing it while the
 * originating touch-down is still bubbling causes that listener to close the
 * menu immediately.</p>
 */
final class ItemTreeContextMenuInputListener<T> extends InputListener {
    interface Presenter<T> {
        void show(EntityNode node, T context, Stage stage, float stageX, float stageY);
    }

    private final IdVisTree tree;
    private final Predicate<EntityNode> supportsContextMenu;
    private final Consumer<EntityNode> activateNode;
    private final Supplier<T> contextSupplier;
    private final Presenter<T> presenter;

    private int pressedPointer = -1;
    private EntityNode pressedNode;
    private T pressedContext;

    ItemTreeContextMenuInputListener(
            IdVisTree tree,
            Predicate<EntityNode> supportsContextMenu,
            Consumer<EntityNode> activateNode,
            Supplier<T> contextSupplier,
            Presenter<T> presenter) {
        this.tree = tree;
        this.supportsContextMenu = supportsContextMenu;
        this.activateNode = activateNode;
        this.contextSupplier = contextSupplier;
        this.presenter = presenter;
    }

    @Override
    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
        clearPressedNode();
        if (button != Input.Buttons.RIGHT || tree.getStage() == null) return false;

        EntityNode node = tree.getNodeAt(y);
        if (node == null || !supportsContextMenu.test(node)) return false;

        pressedPointer = pointer;
        pressedNode = node;
        pressedContext = contextSupplier != null ? contextSupplier.get() : null;
        activateNode.accept(node);
        return true;
    }

    @Override
    public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
        if (button != Input.Buttons.RIGHT
                || pointer != pressedPointer
                || pressedNode == null) {
            return;
        }

        EntityNode node = pressedNode;
        T context = pressedContext;
        clearPressedNode();
        Stage stage = event.getStage();
        if (stage != null) {
            presenter.show(node, context, stage, event.getStageX(), event.getStageY());
            event.handle();
        }
    }

    private void clearPressedNode() {
        pressedPointer = -1;
        pressedNode = null;
        pressedContext = null;
    }
}
