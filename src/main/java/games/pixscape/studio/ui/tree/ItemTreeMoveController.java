package games.pixscape.studio.ui.tree;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.studio.document.SceneEditorDocument;
import games.pixscape.studio.history.commands.ItemMovePreparation;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;

import java.util.*;

/**
 * Items-only gesture ownership. Business compatibility and normalization belong to preparation.
 */
final class ItemTreeMoveController {
    private record Target(EntityNode parent, EntityNode anchor, boolean after) {
    }

    private final StudioApplicationAdapter app;
    private final ItemMoveTree tree;
    private final VisScrollPane scroll;
    private final VisLabel feedback;
    private final Vector2 pointer = new Vector2(), local = new Vector2();
    private final Map<Integer, EntityNode> nodes = new HashMap<>();
    private SceneEditorDocument document;
    private Stage stage;
    private long revision, generation;
    private List<Integer> sources;
    private ItemMovePreparation.Prepared prepared;
    private Target target;
    private EntityNode pressed;
    private float lastX = Float.NaN, lastY, lastScroll;
    private float viewportWidth, viewportHeight;
    private final InputListener escape = new InputListener() {
        @Override
        public boolean keyDown(InputEvent event, int keycode) {
            if (document == null || keycode != Input.Keys.ESCAPE) return false;
            cancel();
            event.stop();
            return true;
        }
    };

    ItemTreeMoveController(StudioApplicationAdapter app, ItemMoveTree tree, VisScrollPane scroll, VisLabel feedback) {
        this.app = app;
        this.tree = tree;
        this.scroll = scroll;
        this.feedback = feedback;
        tree.addCaptureListener(new DragListener() {
            {
                setTapSquareSize(4);
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int p, int button) {
                pressed = tree.getNodeAt(y);
                if (p != 0 || button != Input.Buttons.LEFT || !movable(pressed)
                        || x < pressed.getActor().getX() || !(app.getEditorDocumentManager().activeDocument() instanceof SceneEditorDocument)
                        || !app.getCanvas().getStudioEditingModeService().allowsWorldEditingActions()) return false;
                for (Actor a = event.getTarget(); a != null && a != tree; a = a.getParent())
                    if (a instanceof Button) return false;
                return super.touchDown(event, x, y, p, button);
            }

            @Override
            public void dragStart(InputEvent event, float x, float y, int p) {
                // Tree selection is committed by ClickListener on release, not at press.
                // Cancel all its click activations, including a later cancelled drag.
                for (var listener : tree.getListeners()) if (listener instanceof ClickListener click) click.cancel();
                start();
            }

            @Override
            public void drag(InputEvent event, float x, float y, int p) {
                pointer.set(event.getStageX(), event.getStageY());
                update(0);
            }

            @Override
            public void dragStop(InputEvent event, float x, float y, int p) {
                if (document == null) return;
                pointer.set(event.getStageX(), event.getStageY());
                update(0);
                var plan = prepared;
                var owner = document;
                boolean apply = !event.isTouchFocusCancel() && valid() && plan != null;
                ItemTreeMoveController.this.cancel();
                // Refuse a stale projection, never replace it with an unseen result at release.
                if (apply) owner.context().historyManager().execute(plan.command());
            }
        });
    }

    private static boolean movable(EntityNode node) {
        return node != null && (node.isEntityNode() || node.isTiledMapNode());
    }

    private void start() {
        cancel();
        if (!(app.getEditorDocumentManager().activeDocument() instanceof SceneEditorDocument scene)) return;
        feedbackHeight(18);
        document = scene;
        stage = tree.getStage();
        viewportWidth = scroll.getWidth();
        viewportHeight = scroll.getHeight();
        revision = scene.context().historyManager().getMutationRevision();
        generation = scene.context().generation();
        var ids = scene.context().identityRegistry();
        List<Integer> selected = new ArrayList<>();
        if (tree.getSelection().contains(pressed)) {
            for (EntityNode node : tree.getSelection())
                if (movable(node)) selected.add(ids.getStableId(node.getEntityId()));
        } else selected.add(ids.getStableId(pressed.getEntityId()));
        sources = List.copyOf(selected);
        for (EntityNode node : tree.getRootNodes()) index(node);
        stage.addCaptureListener(escape);
    }

    private void index(EntityNode node) {
        if (node.isEntityNode() || node.isTiledMapNode() || node.isLayerNode())
            nodes.put(document.context().identityRegistry().getStableId(node.getEntityId()), node);
        for (Object child : node.getChildren()) index((EntityNode) child);
    }

    private boolean valid() {
        if (document == null || document.isClosed() || document.context().isDisposed()
                || document != app.getEditorDocumentManager().activeDocument() || tree.getStage() != stage
                || document.context().generation() != generation
                || document.context().historyManager().getMutationRevision() != revision
                || viewportWidth != scroll.getWidth() || viewportHeight != scroll.getHeight()
                || !app.getCanvas().getStudioEditingModeService().allowsWorldEditingActions()) return false;
        for (Actor a = tree; a != null; a = a.getParent()) if (!a.isVisible()) return false;
        return true;
    }

    void cancel() {
        if (stage != null) stage.removeCaptureListener(escape);
        tree.preview(null);
        feedback.setText("");
        prepared = null;
        document = null;
        stage = null;
        target = null;
        sources = null;
        nodes.clear();
        lastX = Float.NaN;
        feedbackHeight(0);
    }

    private void feedbackHeight(float height) {
        if (!(feedback.getParent() instanceof VisTable parent)) return;
        var cell = parent.getCell(feedback);
        if (cell.getPrefHeight() == height) return;
        cell.height(height);
        parent.invalidateHierarchy();
        parent.validate();
        scroll.validate();
    }

    void update(float delta) {
        if (document == null) return;
        if (!valid()) {
            cancel();
            return;
        }
        scroll.validate();
        Actor hit = stage.hit(pointer.x, pointer.y, true);
        scroll.stageToLocalCoordinates(local.set(pointer));
        float bottom = scroll.getStyle().background == null ? 0 : scroll.getStyle().background.getBottomHeight();
        float height = scroll.getScrollHeight();
        if (hit == null || !(hit == scroll || hit.isDescendantOf(scroll)) || local.x < 0
                || local.x >= scroll.getScrollWidth() || local.y < bottom || local.y > bottom + height) {
            target = null;
            prepared = null;
            tree.preview(null);
            feedback.setText("");
            lastX = Float.NaN;
            return;
        }
        float edge = Math.min(28, height / 3), y = local.y - bottom;
        float speed = y < edge ? 1 - y / edge : y > height - edge ? -(y - height + edge) / edge : 0;
        if (speed != 0 && delta > 0) {
            scroll.setScrollY(scroll.getScrollY() + speed * 240 * Math.min(delta, .05f));
            scroll.updateVisualScroll();
            scroll.layout();
        }
        // Projection changes must not retarget a stationary pointer. Edge scrolling still
        // invalidates this hit test; history/context validity is checked above on every act.
        if (pointer.x == lastX && pointer.y == lastY && scroll.getVisualScrollY() == lastScroll) return;
        lastX = pointer.x;
        lastY = pointer.y;
        lastScroll = scroll.getVisualScrollY();
        tree.stageToLocalCoordinates(local.set(pointer));
        EntityNode node = null;
        float fraction = 0, hitTop = 0;
        if (tree.preview() != null) {
            var row = tree.previewRowAt(local.y);
            if (row != null) {
                if (row.ghost()) return; // The whole reserved ghost area denotes the same destination.
                node = row.node();
                fraction = (row.top() - local.y) / row.height();
                hitTop = row.top();
            }
        } else {
            node = tree.getNodeAt(local.y);
            if (node != null) {
                fraction = (node.getActor().getTop() - local.y) / node.getActor().getHeight();
                hitTop = node.getActor().getTop() + tree.getYSpacing() / 2;
            }
        }
        if (node == null || !(node.isLayerNode() || node.isEntityNode() || node.isTiledMapNode())) {
            if (prepared != null) hideGhosts();
            prepared = null;
            target = null;
            feedback.setText("Drop on an item or layer");
            return;
        }
        boolean inside = node.isLayerNode() || (fraction >= .25f && fraction <= .75f
                && document.context().world().getMapper(GameObjectComponent.class).has(node.getEntityId()));
        Target next = inside ? new Target(node, null, false) : new Target((EntityNode) node.getParent(), node, fraction > .5f);
        if (next.equals(target)) return;
        target = next;
        // Retain only the displayed destination, not a scene-sized plan for every visited row.
        var result = prepare(next);
        if (!result.accepted()) {
            if (prepared != null) hideGhosts();
            prepared = null;
            feedback.setText(result.refusal());
            return;
        }
        prepared = result.move();
        feedback.setText(inside ? "Inside " + node.getLabel().getText() : (next.after ? "After " : "Before ") + node.getLabel().getText());
        float hitStageY = tree.localToStageCoordinates(local.set(0, hitTop)).y;
        // Explicit center hover opens the destination immediately; no delayed task can outlive a drag.
        next.parent.setExpanded(true);
        tree.invalidate();
        tree.validate();
        scroll.layout();
        lastScroll = scroll.getVisualScrollY();
        showPreview(node, hitStageY);
    }

    private void hideGhosts() {
        // A refusal removes feedback, not the coordinate system used by the next input event.
        // Retain the displayed rows and their positions until another destination or panel exit.
        if (tree.preview() != null) tree.preview(tree.preview().stream().filter(row -> !row.ghost()).toList());
    }

    private ItemMovePreparation.Result prepare(Target target) {
        if (target.parent == null) return new ItemMovePreparation.Result(null, "Drop inside a layer");
        var context = document.context();
        var ids = context.identityRegistry();
        int parent = target.parent.isLayerNode() ? 0 : ids.getStableId(target.parent.getEntityId());
        int layer = target.parent.isLayerNode() ? target.parent.getEntityId() : context.layerService().getEffectiveLayerEntity(target.parent.getEntityId());
        var destination = new ItemMovePreparation.Destination(document, ids.getStableId(layer), parent, 0);
        var first = ItemMovePreparation.prepare(document, sources, destination);
        if (!first.accepted() || target.anchor == null) return first;
        var group = new ItemMovePreparation.Group(parent == 0 ? destination.layerStableId() : 0, parent);
        var order = first.move().orders().stream().filter(o -> o.group().equals(group)).findFirst().orElseThrow();
        Set<Integer> moving = new HashSet<>(first.move().sources());
        int anchor = ids.getStableId(target.anchor.getEntityId()), slot = 0;
        for (int id : order.before()) {
            if (id == anchor) {
                if (target.after && !moving.contains(id)) slot++;
                break;
            }
            if (!moving.contains(id)) slot++;
        }
        return slot == 0 ? first : ItemMovePreparation.prepare(document, sources,
                new ItemMovePreparation.Destination(document, destination.layerStableId(), parent, slot));
    }

    private void showPreview(EntityNode hit, float hitStageY) {
        List<ItemMoveTree.Row> rows = new ArrayList<>();
        Set<Integer> moving = new HashSet<>(prepared.sources());
        int parent = prepared.destination().parentStableId();
        var group = new ItemMovePreparation.Group(parent == 0 ? prepared.destination().layerStableId() : 0, parent);
        var order = prepared.orders().stream().filter(o -> o.group().equals(group)).findFirst().orElseThrow();
        List<Integer> following = order.after().subList(prepared.destination().insertionIndex() + prepared.sources().size(), order.after().size());
        EntityNode anchor = following.isEmpty() ? null : nodes.get(following.getFirst());
        for (EntityNode root : tree.getRootNodes()) project(root, 0, moving, target.parent, anchor, rows);
        float top = tree.getRootNodes().first().getActor().getTop() + tree.getYSpacing() / 2;
        float bottom = top, anchorTop = top;
        for (var row : rows) {
            if (row.node() == hit) anchorTop = bottom;
            bottom -= row.height();
        }
        EntityNode last = tree.getRootNodes().peek();
        while (last.isExpanded() && last.getChildren().notEmpty()) last = (EntityNode) last.getChildren().peek();
        float contentBottom = last.getActor().getY() - tree.getYSpacing() / 2;
        float desiredTop = tree.stageToLocalCoordinates(local.set(0, hitStageY)).y;
        // Compact sources can remove many descendant rows. Anchor the hovered row instead
        // of pulling it away from the pointer. Use only the space freed within the original
        // content extent: scrolling stays bounded and every remaining row stays reachable.
        top += Math.max(contentBottom - bottom, Math.min(0, desiredTop - anchorTop));
        List<ItemMoveTree.Row> positioned = new ArrayList<>();
        for (var row : rows) {
            positioned.add(new ItemMoveTree.Row(row.node(), row.depth(), row.ghost(), top, row.height()));
            top -= row.height();
        }
        tree.preview(List.copyOf(positioned));
    }

    private void project(EntityNode node, int depth, Set<Integer> moving, EntityNode parent, EntityNode anchor, List<ItemMoveTree.Row> rows) {
        int stable = document.context().identityRegistry().getStableId(node.getEntityId());
        if ((node.isEntityNode() || node.isTiledMapNode()) && moving.contains(stable)) return;
        if (node == anchor) ghosts(depth, rows);
        rows.add(new ItemMoveTree.Row(node, depth, false, 0, node.getActor().getHeight() + tree.getYSpacing()));
        if (node.isExpanded()) for (Object child : node.getChildren())
            project((EntityNode) child, depth + 1, moving, parent, anchor, rows);
        if (node == parent && anchor == null) ghosts(depth + 1, rows);
    }

    private void ghosts(int depth, List<ItemMoveTree.Row> rows) {
        for (int id : prepared.sources()) {
            EntityNode node = nodes.get(id);
            rows.add(new ItemMoveTree.Row(node, depth, true, 0, node.getActor().getHeight() + tree.getYSpacing()));
        }
    }
}
